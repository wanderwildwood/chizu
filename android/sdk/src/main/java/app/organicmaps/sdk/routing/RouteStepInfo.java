package app.organicmaps.sdk.routing;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import app.organicmaps.sdk.util.Distance;

/**
 * Represents routing::RouteStepInfo class
 */
@Keep
@SuppressWarnings("unused")
public final class RouteStepInfo
{
    public final int index;
    public final int sequence;
    public final int turnDirection;
    public final int pedestrianDirection;
    @Nullable
    public final String fromStreetName;
    @Nullable
    public final String toStreetName;
    public final int exitNum;
    public final double distMeters;
    public final Distance formattedDistance;
    public final String textualInstruction;
    // Atlas: what a highway exit leads to, so a row can say more than "Exit".
    @Nullable
    public final String toRef;
    @Nullable
    public final String junctionRef;
    @Nullable
    public final String destinationRef;
    @Nullable
    public final String destination;

    private RouteStepInfo(int index, int sequence, int turnDirection, int pedestrianDirection,
                          @Nullable String fromStreetName, @Nullable String toStreetName, int exitNum,
                          double distMeters, @Nullable Distance formattedDistance, String textualInstruction,
                          @Nullable String toRef, @Nullable String junctionRef, @Nullable String destinationRef,
                          @Nullable String destination)
    {
        this.index = index;
        this.sequence = sequence;
        this.turnDirection = turnDirection;
        this.pedestrianDirection = pedestrianDirection;
        this.fromStreetName = fromStreetName;
        this.toStreetName = toStreetName;
        this.exitNum = exitNum;
        this.distMeters = distMeters;
        this.formattedDistance = formattedDistance;
        this.textualInstruction = textualInstruction;
        this.toRef = toRef;
        this.junctionRef = junctionRef;
        this.destinationRef = destinationRef;
        this.destination = destination;
    }

    public int getTurnDrawableRes()
    {
        if (turnDirection > 0 && turnDirection < CarDirection.values().length)
            return CarDirection.values()[turnDirection].getTurnRes();

        if (pedestrianDirection > 0 && pedestrianDirection < PedestrianTurnDirection.values().length)
            return PedestrianTurnDirection.values()[pedestrianDirection].getTurnRes();

        return 0;
    }

}
