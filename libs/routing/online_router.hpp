#pragma once

#include "routing/router.hpp"
#include "routing/vehicle_mask.hpp"

#include <string>

namespace routing
{
/// A router that reads no map file: it asks a Valhalla server for the route and fills
/// the Route with the line, the turns, the street names and the times the server gave.
/// RoutingSession follows that route as it follows any other.
class OnlineRouter : public IRouter
{
public:
  /// The address used when the person has not typed one of their own.
  static char const * const kDefaultServerUrl;

  OnlineRouter(VehicleType vehicleType, std::string serverUrl);

  std::string GetName() const override { return {"online-router"}; }
  void ClearState() override {}
  void SetGuides(GuidesTracks && guides) override {}
  RouterResultCode CalculateRoute(Checkpoints const & checkpoints, m2::PointD const & startDirection,
                                  bool adjustToPrevRoute, RouterDelegate const & delegate, Route & route) override;
  bool FindClosestProjectionToRoad(m2::PointD const & point, m2::PointD const & direction, double radius,
                                   EdgeProj & proj) override;

private:
  VehicleType const m_vehicleType;
  std::string const m_serverUrl;
};
}  // namespace routing
