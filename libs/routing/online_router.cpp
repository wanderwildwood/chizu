#include "routing/online_router.hpp"

#include "routing/checkpoints.hpp"
#include "routing/route.hpp"
#include "routing/router_delegate.hpp"
#include "routing/routing_callbacks.hpp"
#include "routing/routing_helpers.hpp"
#include "routing/routing_options.hpp"
#include "routing/segment.hpp"
#include "routing/turns.hpp"

#include "routing_common/num_mwm_id.hpp"

#include "platform/http_client.hpp"

#include "geometry/mercator.hpp"
#include "geometry/point_with_altitude.hpp"

#include "base/logging.hpp"

#include "cppjansson/cppjansson.hpp"

#include <algorithm>
#include <chrono>
#include <mutex>
#include <sstream>
#include <thread>
#include <vector>

namespace routing
{
using namespace std;

namespace
{
// The public servers ask for at most one request a second. A reroute, a second route to
// choose from and a new destination all come through here, so the gap is kept here.
auto constexpr kMinGapBetweenRequests = chrono::milliseconds(1200);
// A phone that stays off the line asks for a new route again and again. The servers are run by
// volunteers: such requests wait longer.
auto constexpr kMinGapBetweenReroutes = chrono::seconds(8);
double constexpr kRequestTimeoutSec = 20.0;
char const * const kUserAgent = "Atlas (com.wanderwildwood.chizu; https://github.com/wanderwildwood/chizu)";

mutex g_requestMutex;
chrono::steady_clock::time_point g_lastRequest;

// Valhalla's maneuver types (its api/turn-by-turn reference, "type").
enum ValhallaManeuver
{
  kStart = 1,
  kStartRight = 2,
  kStartLeft = 3,
  kDestination = 4,
  kDestinationRight = 5,
  kDestinationLeft = 6,
  kBecomes = 7,
  kContinue = 8,
  kSlightRight = 9,
  kRight = 10,
  kSharpRight = 11,
  kUturnRight = 12,
  kUturnLeft = 13,
  kSharpLeft = 14,
  kLeft = 15,
  kSlightLeft = 16,
  kRampStraight = 17,
  kRampRight = 18,
  kRampLeft = 19,
  kExitRight = 20,
  kExitLeft = 21,
  kStayStraight = 22,
  kStayRight = 23,
  kStayLeft = 24,
  kMerge = 25,
  kRoundaboutEnter = 26,
  kRoundaboutExit = 27,
  kMergeRight = 37,
  kMergeLeft = 38,
};

turns::CarDirection ToCarDirection(int type)
{
  using turns::CarDirection;
  switch (type)
  {
  case kSlightRight:
  case kRampRight:
  case kStayRight: return CarDirection::TurnSlightRight;
  case kRight: return CarDirection::TurnRight;
  case kSharpRight: return CarDirection::TurnSharpRight;
  case kSlightLeft:
  case kRampLeft:
  case kStayLeft: return CarDirection::TurnSlightLeft;
  case kLeft: return CarDirection::TurnLeft;
  case kSharpLeft: return CarDirection::TurnSharpLeft;
  case kUturnRight: return CarDirection::UTurnRight;
  case kUturnLeft: return CarDirection::UTurnLeft;
  case kExitRight: return CarDirection::ExitHighwayToRight;
  case kExitLeft: return CarDirection::ExitHighwayToLeft;
  case kRampStraight:
  case kStayStraight:
  case kMerge:
  case kMergeRight:
  case kMergeLeft: return CarDirection::GoStraight;
  case kRoundaboutEnter: return CarDirection::EnterRoundAbout;
  case kRoundaboutExit: return CarDirection::LeaveRoundAbout;
  default: return CarDirection::None;  // Start, destination, continue, becomes, ferries.
  }
}

turns::PedestrianDirection ToPedestrianDirection(int type)
{
  using turns::PedestrianDirection;
  switch (type)
  {
  case kSlightRight:
  case kRight:
  case kSharpRight:
  case kUturnRight:
  case kRampRight:
  case kExitRight:
  case kStayRight: return PedestrianDirection::TurnRight;
  case kSlightLeft:
  case kLeft:
  case kSharpLeft:
  case kUturnLeft:
  case kRampLeft:
  case kExitLeft:
  case kStayLeft: return PedestrianDirection::TurnLeft;
  default: return PedestrianDirection::None;
  }
}

bool IsRampOrExit(int type)
{
  return type == kRampStraight || type == kRampRight || type == kRampLeft || type == kExitRight || type == kExitLeft;
}

char const * Costing(VehicleType type)
{
  switch (type)
  {
  case VehicleType::Pedestrian: return "pedestrian";
  case VehicleType::Bicycle: return "bicycle";
  default: return "auto";
  }
}

// Valhalla's encoded line: Google's polyline algorithm with six decimal places.
bool DecodeShape(string const & encoded, vector<m2::PointD> & points)
{
  double constexpr kPrecision = 1e-6;
  size_t i = 0;
  int64_t lat = 0;
  int64_t lon = 0;

  auto const next = [&](int64_t & value)
  {
    int64_t result = 0;
    int shift = 0;
    while (true)
    {
      if (i >= encoded.size() || shift > 60)
        return false;
      int64_t const b = static_cast<int64_t>(encoded[i++]) - 63;
      if (b < 0)
        return false;
      result |= (b & 0x1f) << shift;
      shift += 5;
      if (b < 0x20)
        break;
    }
    value += (result & 1) ? ~(result >> 1) : (result >> 1);
    return true;
  };

  while (i < encoded.size())
  {
    if (!next(lat) || !next(lon))
      return false;
    double const latDeg = static_cast<double>(lat) * kPrecision;
    double const lonDeg = static_cast<double>(lon) * kPrecision;
    if (latDeg < -90.0 || latDeg > 90.0 || lonDeg < -180.0 || lonDeg > 180.0)
      return false;
    points.push_back(mercator::FromLatLon(latDeg, lonDeg));
  }
  return true;
}

string StringAt(json_t const * array, size_t index)
{
  if (!json_is_array(array) || index >= json_array_size(array))
    return {};
  char const * s = json_string_value(json_array_get(array, index));
  return s ? string(s) : string();
}

// The first text of one of the lists in a maneuver's "sign" (each entry is {"text": ...}).
string SignText(json_t const * sign, char const * key)
{
  if (!json_is_object(sign))
    return {};
  json_t const * list = json_object_get(sign, key);
  if (!json_is_array(list) || json_array_size(list) == 0)
    return {};
  char const * s = json_string_value(json_object_get(json_array_get(list, 0), "text"));
  return s ? string(s) : string();
}

string BuildRequest(vector<m2::PointD> const & points, size_t from, VehicleType vehicleType)
{
  ostringstream os;
  os.precision(7);
  os << fixed;
  os << "{\"locations\":[";
  for (size_t i = from; i < points.size(); ++i)
  {
    auto const ll = mercator::ToLatLon(points[i]);
    if (i != from)
      os << ",";
    os << "{\"lat\":" << ll.m_lat << ",\"lon\":" << ll.m_lon << "}";
  }
  os << "],\"costing\":\"" << Costing(vehicleType) << "\"";

  // What the person chose to avoid (Routing options, and "No highways" beside the route).
  auto const avoid = RoutingOptions::LoadOptionsFromSettings(vehicleType);
  string options;
  auto const add = [&options](char const * key)
  {
    options += options.empty() ? "" : ",";
    options += string("\"") + key + "\":0";
  };
  if (vehicleType == VehicleType::Car && avoid.Has(RoutingOptions::Motorway))
    add("use_highways");
  if (vehicleType == VehicleType::Car && avoid.Has(RoutingOptions::Toll))
    add("use_tolls");
  if (avoid.Has(RoutingOptions::Ferry))
    add("use_ferry");
  if (!options.empty())
    os << ",\"costing_options\":{\"" << Costing(vehicleType) << "\":{" << options << "}}";

  os << ",\"units\":\"kilometers\"}";
  return os.str();
}

// Waits until the gap since the last request has passed. Returns false when cancelled meanwhile.
bool WaitForTurn(RouterDelegate const & delegate, bool reroute)
{
  chrono::steady_clock::duration const gap = reroute ? chrono::steady_clock::duration(kMinGapBetweenReroutes)
                                                     : chrono::steady_clock::duration(kMinGapBetweenRequests);
  while (true)
  {
    {
      lock_guard<mutex> lock(g_requestMutex);
      auto const now = chrono::steady_clock::now();
      if (g_lastRequest.time_since_epoch().count() == 0 || now - g_lastRequest >= gap)
      {
        g_lastRequest = now;
        return true;
      }
    }
    if (delegate.IsCancelled())
      return false;
    this_thread::sleep_for(chrono::milliseconds(100));
  }
}

geometry::PointWithAltitude ToJunction(m2::PointD const & p)
{
  return geometry::PointWithAltitude(p, geometry::kDefaultAltitudeMeters);
}
}  // namespace

// static
char const * const OnlineRouter::kDefaultServerUrl = "https://valhalla1.openstreetmap.de";

OnlineRouter::OnlineRouter(VehicleType vehicleType, string serverUrl)
  : m_vehicleType(vehicleType)
  , m_serverUrl(serverUrl.empty() ? string(kDefaultServerUrl) : std::move(serverUrl))
{}

/* How the route is laid out (the same rule as the other routers: one more point than segments,
   and segment i ends at point i + 1).

   For a leg whose line is Q0 Q1 ... Qm the segments end at Q0, Q1, ..., Qm: the first one has no
   length and stands for the start, as the fake start loop does in a route from a map file.
   The first leg puts Q0 into the geometry twice; a later leg starts on the point the leg before
   ended on, so it adds Q0 once.

   A maneuver that begins at Qb is a turn at the end of the segment that ends at Qb.
   TurnItem::m_index is that segment's index + 1. */
RouterResultCode OnlineRouter::CalculateRoute(Checkpoints const & checkpoints, m2::PointD const & /* startDirection */,
                                              bool adjustToPrevRoute, RouterDelegate const & delegate,
                                              Route & route)
{
  vector<m2::PointD> const & stops = checkpoints.GetPoints();
  size_t const passed = checkpoints.GetPassedIdx();
  if (stops.size() < 2 || passed + 1 >= stops.size())
    return RouterResultCode::RouteNotFound;

  // The session passes adjustToPrevRoute when it rebuilds a route the phone has left.
  if (!WaitForTurn(delegate, adjustToPrevRoute /* reroute */))
    return RouterResultCode::Cancelled;

  string url = m_serverUrl;
  while (!url.empty() && url.back() == '/')
    url.pop_back();
  url += "/route";

  platform::HttpClient request(url);
  request.SetBodyData(BuildRequest(stops, passed, m_vehicleType), "application/json");
  request.SetRawHeader("User-Agent", kUserAgent);
  request.SetRawHeader("Accept", "application/json");
  request.SetTimeout(kRequestTimeoutSec);

  if (!request.RunHttpRequest())
  {
    LOG(LWARNING, ("Online route: no answer from", url));
    return RouterResultCode::TransitRouteNotFoundNoNetwork;
  }
  if (delegate.IsCancelled())
    return RouterResultCode::Cancelled;
  if (request.ErrorCode() != 200)
  {
    // Valhalla answers 400 with a text when it finds no road near a point or no way between them.
    LOG(LWARNING, ("Online route: the server answered", request.ErrorCode()));
    return request.ErrorCode() == 400 ? RouterResultCode::RouteNotFound : RouterResultCode::InternalError;
  }

  vector<RouteSegment> segments;
  vector<double> times;
  vector<m2::PointD> geometry;
  vector<Route::SubrouteAttrs> subroutes;

  for (size_t i = 0; i < passed; ++i)
    subroutes.emplace_back(ToJunction(stops[i]), ToJunction(stops[i + 1]), 0 /* begin */, 0 /* end */);

  Segment const fakeSegment(kFakeNumMwmId, 0, 0, false);
  bool const isPedestrian = m_vehicleType == VehicleType::Pedestrian;

  try
  {
    base::Json const root(request.ServerResponse());
    json_t const * legs = json_object_get(json_object_get(root.get(), "trip"), "legs");
    if (!json_is_array(legs) || json_array_size(legs) == 0)
      return RouterResultCode::RouteNotFound;

    double timeSec = 0.0;

    for (size_t legIdx = 0; legIdx < json_array_size(legs); ++legIdx)
    {
      json_t const * leg = json_array_get(legs, legIdx);
      char const * shape = json_string_value(json_object_get(leg, "shape"));
      vector<m2::PointD> line;
      if (!shape || !DecodeShape(shape, line) || line.size() < 2)
        return RouterResultCode::InternalError;

      size_t const m = line.size() - 1;
      size_t const legBase = segments.size();

      // What each segment of this leg carries; filled from the maneuvers below.
      vector<turns::TurnItem> legTurns(m + 1);
      vector<RouteSegment::RoadNameInfo> legNames(m + 1);
      vector<double> legTimes(m + 1, 0.0);  // Seconds spent on the segment.

      // The exit number of the roundabout being driven. Valhalla gives it where the roundabout is
      // entered (or on the first maneuver, for a route that starts inside one), not where it is left.
      uint32_t roundaboutExit = 0;

      json_t const * maneuvers = json_object_get(leg, "maneuvers");
      size_t const maneuverCount = json_is_array(maneuvers) ? json_array_size(maneuvers) : 0;
      for (size_t k = 0; k < maneuverCount; ++k)
      {
        json_t const * mn = json_array_get(maneuvers, k);
        int const type = static_cast<int>(json_integer_value(json_object_get(mn, "type")));
        size_t const begin = min(m, static_cast<size_t>(json_integer_value(json_object_get(mn, "begin_shape_index"))));
        size_t const end = min(m, static_cast<size_t>(json_integer_value(json_object_get(mn, "end_shape_index"))));

        if (json_t const * count = json_object_get(mn, "roundabout_exit_count"); json_is_integer(count))
          roundaboutExit = static_cast<uint32_t>(json_integer_value(count));

        // The turn, at the point where the maneuver begins.
        if (begin > 0)
        {
          uint32_t const turnIndex = static_cast<uint32_t>(legBase + begin + 1);
          if (isPedestrian)
          {
            auto const dir = ToPedestrianDirection(type);
            if (dir != turns::PedestrianDirection::None)
              legTurns[begin] = turns::TurnItem(turnIndex, dir);
          }
          else
          {
            auto const dir = ToCarDirection(type);
            if (dir != turns::CarDirection::None)
            {
              uint32_t exitNum = 0;
              if (dir == turns::CarDirection::EnterRoundAbout || dir == turns::CarDirection::LeaveRoundAbout)
                exitNum = roundaboutExit;
              if (dir == turns::CarDirection::LeaveRoundAbout)
                roundaboutExit = 0;
              legTurns[begin] = turns::TurnItem(turnIndex, dir, exitNum);
            }
          }
        }

        if (end <= begin)
          continue;

        // The road the maneuver runs on. Every segment carries it, so the name is found from
        // anywhere along the road.
        json_t const * names = json_object_get(mn, "street_names");
        json_t const * sign = json_object_get(mn, "sign");
        // Valhalla lists names and road numbers together ("West Main Street", "US 20"). The first
        // entry is the name; a later one counts as the number only if it has a digit in it and is
        // not the name again.
        string const name = StringAt(names, 0);
        string ref;
        for (size_t n = 1; json_is_array(names) && n < json_array_size(names) && ref.empty(); ++n)
        {
          string const candidate = StringAt(names, n);
          if (candidate != name && any_of(candidate.begin(), candidate.end(), [](char c) { return c >= '0' && c <= '9'; }))
            ref = candidate;
        }
        RouteSegment::RoadNameInfo info(name, ref,
                                        SignText(sign, "exit_number_elements") /* junction_ref */,
                                        SignText(sign, "exit_branch_elements") /* destination_ref */,
                                        SignText(sign, "exit_toward_elements") /* destination */, IsRampOrExit(type));

        // The maneuver's time, shared among its segments by their length.
        double const maneuverSec = max(0.0, json_number_value(json_object_get(mn, "time")));
        double lengthM = 0.0;
        for (size_t j = begin + 1; j <= end; ++j)
          lengthM += mercator::DistanceOnEarth(line[j - 1], line[j]);

        for (size_t j = begin + 1; j <= end; ++j)
        {
          legNames[j] = info;
          double const part = lengthM > 0.0 ? mercator::DistanceOnEarth(line[j - 1], line[j]) / lengthM
                                            : 1.0 / static_cast<double>(end - begin);
          legTimes[j] = maneuverSec * part;
        }
      }

      // The arrival, on the last segment of the last leg.
      if (legIdx + 1 == json_array_size(legs))
      {
        uint32_t const turnIndex = static_cast<uint32_t>(legBase + m + 1);
        legTurns[m] = isPedestrian ? turns::TurnItem(turnIndex, turns::PedestrianDirection::ReachedYourDestination)
                                   : turns::TurnItem(turnIndex, turns::CarDirection::ReachedYourDestination);
      }

      for (size_t j = 0; j <= m; ++j)
      {
        timeSec += legTimes[j];
        segments.emplace_back(fakeSegment, legTurns[j], ToJunction(line[j]), legNames[j]);
        times.push_back(timeSec);
      }

      if (legIdx == 0)
        geometry.push_back(line[0]);
      geometry.insert(geometry.end(), line.begin(), line.end());

      subroutes.emplace_back(ToJunction(line.front()), ToJunction(line.back()), legBase, segments.size());
    }
  }
  catch (base::Json::Exception const & e)
  {
    LOG(LWARNING, ("Online route: the answer could not be read:", e.Msg()));
    return RouterResultCode::InternalError;
  }

  // The server must have answered with one leg for each stretch that is still ahead.
  if (subroutes.size() + 1 != stops.size())
    return RouterResultCode::InternalError;

  if (delegate.IsCancelled())
    return RouterResultCode::Cancelled;

  FillSegmentInfo(times, segments);
  route.SetRouteSegments(std::move(segments));
  route.SetCurrentSubrouteIdx(passed);
  route.SetSubroteAttrs(std::move(subroutes));
  route.SetGeometry(geometry.begin(), geometry.end());
  route.MatchPositionToAllSegments();
  return RouterResultCode::NoError;
}

bool OnlineRouter::FindClosestProjectionToRoad(m2::PointD const & /* point */, m2::PointD const & /* direction */,
                                               double /* radius */, EdgeProj & /* proj */)
{
  // There is no road graph on the phone to project onto.
  return false;
}
}  // namespace routing
