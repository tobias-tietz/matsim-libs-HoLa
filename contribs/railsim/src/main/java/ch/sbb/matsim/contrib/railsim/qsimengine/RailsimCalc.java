package ch.sbb.matsim.contrib.railsim.qsimengine;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class holding static calculation methods related to state (updates).
 */
public class RailsimCalc {

	private RailsimCalc() {
	}

	/**
	 * Calculate traveled distance given initial speed and constant acceleration.
	 */
	static double calcTraveledDist(double speed, double elapsedTime, double acceleration) {
		return speed * elapsedTime + (elapsedTime * elapsedTime * acceleration / 2);
	}

	/**
	 * Inverse of {@link #calcTraveledDist(double, double, double)}, solves for distance.
	 */
	static double solveTraveledDist(double speed, double dist, double acceleration) {
		if (acceleration == 0)
			return dist / speed;

		return (Math.sqrt(2 * acceleration * dist + speed * speed) - speed) / acceleration;
	}

	/**
	 * Calculate time needed to advance distance {@code dist}. Depending on acceleration and max speed.
	 */
	static double calcRequiredTime(TrainState state, double dist) {

		if (FuzzyUtils.equals(dist, 0))
			return 0;

		if (state.acceleration == 0)
			return state.speed == 0 ? Double.POSITIVE_INFINITY : dist / state.speed;

		if (state.acceleration > 0) {

			double accelTime = (state.targetSpeed - state.speed) / state.acceleration;

			double d = calcTraveledDist(state.speed, accelTime, state.acceleration);

			// The required distance is reached during acceleration
			if (d > dist) {
				return solveTraveledDist(state.speed, dist, state.acceleration);

			} else
				// Time for accel plus remaining dist at max speed
				return accelTime + (dist - d) / state.targetSpeed;

		} else {

			double decelTime = -(state.speed - state.targetSpeed) / state.acceleration;

			// max distance that can be reached
			double max = calcTraveledDist(state.speed, decelTime, state.acceleration);

			if (FuzzyUtils.equals(dist, max)) {
				return decelTime;
			} else if (dist <= max) {
				return solveTraveledDist(state.speed, dist, state.acceleration);
			} else
				return Double.POSITIVE_INFINITY;
		}
	}

	/**
	 * Calculate the maximum speed that can be reached under the condition that speed must be reduced to {@code allowedSpeed}
	 * again after traveled {@code dist}.
	 */
	static SpeedTarget calcTargetSpeed(double dist, double acceleration, double deceleration,
									   double currentSpeed, double targetSpeed, double finalSpeed) {

		double timeDecel = (targetSpeed - finalSpeed) / deceleration;
		double distDecel = calcTraveledDist(targetSpeed, timeDecel, -deceleration);

		// This code below only works during deceleration
		if (acceleration <= 0 || currentSpeed >= targetSpeed)
			return new SpeedTarget(targetSpeed, distDecel);

		assert FuzzyUtils.greaterEqualThan(targetSpeed, finalSpeed) : "Final speed must be smaller than target";

		double timeAccel = (targetSpeed - currentSpeed) / acceleration;
		double distAccel = calcTraveledDist(currentSpeed, timeAccel, acceleration);

		// there is enough distance to accelerate to the target speed
		if (distAccel + distDecel < dist)
			return new SpeedTarget(targetSpeed, distDecel);

		double nom = 2 * acceleration * deceleration * dist
			+ acceleration * finalSpeed * finalSpeed
			+ deceleration * currentSpeed * currentSpeed;

		double v = Math.sqrt(nom / (acceleration + deceleration));

		timeDecel = (v - finalSpeed) / deceleration;
		distDecel = calcTraveledDist(v, timeDecel, -deceleration);

		return new SpeedTarget(v, distDecel);
	}

	/**
	 * Calc the distance deceleration needs to start and the target speed.
	 */
	static double calcDecelDistanceAndSpeed(RailLink currentLink, UpdateEvent event) {

		TrainState state = event.state;

		if (state.speed == 0)
			return Double.POSITIVE_INFINITY;

		double assumedSpeed = state.speed;

		double maxSpeed = Math.max(assumedSpeed, state.allowedMaxSpeed);

		// Lookahead window
		double window = RailsimCalc.calcTraveledDist(maxSpeed, maxSpeed / state.train.deceleration(),
			-state.train.deceleration()) + currentLink.length;

		// Distance to the next speed change point (link)
		double dist = currentLink.length - state.headPosition;

		double decelDist = Double.POSITIVE_INFINITY;
		double targetSpeed = state.targetSpeed;
		double speed = 0;

		for (int i = state.routeIdx; i <= state.route.size(); i++) {

			RailLink link;
			double allowed;
			// Last track where train comes to halt
			if (i == state.route.size()) {
				link = null;
				allowed = 0;
			} else {
				link = state.route.get(i);

				// If the previous link is a transit stop the speed needs to be 0 at the next link
				// train stops at the very end of a link
				if (i > 0 && state.isStop(state.route.get(i - 1).getLinkId()))
					allowed = 0;
				else
					allowed = link.getAllowedFreespeed(state.driver);
			}

			if (allowed < assumedSpeed) {

				SpeedTarget target = calcTargetSpeed(dist, state.acceleration, state.train.deceleration(), state.speed, state.targetSpeed, allowed);

				double newDecelDist = dist - target.decelDist;

				if (newDecelDist < decelDist) {
					decelDist = newDecelDist;
					targetSpeed = target.targetSpeed;
					speed = allowed;
				}
			}

			if (link != null)
				dist += link.length;

			// don't need to look further than distance needed for full stop
			if (dist >= window)
				break;
		}

		state.targetSpeed = targetSpeed;

		event.newSpeed = speed;
		return decelDist;
	}

	/**
	 * Calculate when the reservation function should be triggered.
	 * Should return {@link Double#POSITIVE_INFINITY} if this distance is far in the future and can be checked at later point.
	 *
	 * @param state       current train state
	 * @param currentLink the link where the train head is on
	 * @return travel distance after which reservations should be updated.
	 */
	public static double nextLinkReservation(TrainState state, RailLink currentLink) {

		// time needed for full stop
		double assumedSpeed = state.allowedMaxSpeed;
		double stopTime = assumedSpeed / state.train.deceleration();

		assert stopTime > 0 : "Stop time can not be negative";

		// TODO: there is an additional safety factor (also in links to block)
		// this might be reduced, but currently the case when a train stops exactly before the not reserved link is not handled

		// safety distance
		double safety = RailsimCalc.calcTraveledDist(assumedSpeed, stopTime, -state.train.deceleration()) * 1.5;

		double dist = currentLink.length - state.headPosition;

		int idx = state.routeIdx;
		do {
			RailLink nextLink = state.route.get(idx++);

			if (!nextLink.isBlockedBy(state.driver))
				return dist - safety;

			dist += nextLink.length;

		} while (dist <= safety && idx < state.route.size());

		// No need to reserve yet
		return Double.POSITIVE_INFINITY;
	}

	/**
	 * Links that need to be blocked or otherwise stop needs to be initiated.
	 */
	public static List<RailLink> calcLinksToBlock(int idx, TrainState state) {

		List<RailLink> result = new ArrayList<>();

		// safety distance
		double assumedSpeed = state.allowedMaxSpeed;

		double stopTime = assumedSpeed / state.train.deceleration();
		// safety distance
		double safety = RailsimCalc.calcTraveledDist(assumedSpeed, stopTime, -state.train.deceleration()) * 1.5 + state.headPosition;

		double reserved = 0;
		while (reserved < safety && idx < state.route.size()) {
			RailLink nextLink = state.route.get(idx++);
			result.add(nextLink);
			reserved += nextLink.length;
		}

		return result;
	}


	record SpeedTarget(double targetSpeed, double decelDist) implements Comparable<SpeedTarget> {

		@Override
		public int compareTo(SpeedTarget o) {
			return Double.compare(decelDist, o.decelDist);
		}
	}

}
