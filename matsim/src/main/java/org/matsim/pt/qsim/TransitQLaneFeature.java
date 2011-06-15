/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt.qsim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.Queue;

import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.ptproject.qsim.comparators.QVehicleEarliestLinkExitTimeComparator;
import org.matsim.ptproject.qsim.interfaces.NetsimLink;
import org.matsim.ptproject.qsim.qnetsimengine.QVehicle;
import org.matsim.vis.snapshots.writers.AgentSnapshotInfo;
import org.matsim.vis.snapshots.writers.AgentSnapshotInfo.AgentState;
import org.matsim.vis.snapshots.writers.AgentSnapshotInfoFactory;

public class TransitQLaneFeature {

	private static final Comparator<QVehicle> VEHICLE_EXIT_COMPARATOR = new QVehicleEarliestLinkExitTimeComparator();

	/**
	 * A list containing all transit vehicles that are at a stop but not
	 * blocking other traffic on the lane.
	 */
	private final Queue<QVehicle> transitVehicleStopQueue = new PriorityQueue<QVehicle>(5, VEHICLE_EXIT_COMPARATOR);

	private final NetsimLink queueLane;

	public TransitQLaneFeature(final NetsimLink queueLane) {
		this.queueLane = queueLane;
	}

	public boolean isFeatureActive() {
		return !this.transitVehicleStopQueue.isEmpty();
	}

	public Collection<QVehicle> getFeatureVehicles() {
		return this.transitVehicleStopQueue;
	}

	/**
	 * The method name tells when it is called, but not what it does (maybe
	 * because the "Feature" structure is meant to be more general). This method
	 * moves transit vehicles from the stop queue directly to the front of the
	 * "queue" of the QLink. An advantage is that this will observe flow
	 * capacity restrictions. yyyy A disadvantage is that this is a hard-coding
	 * on top of the queue/buffer structure where it is questionable if this
	 * should be used or even visible from the outside. kai, feb'10
	 */
	public void beforeMoveLaneToBuffer(final double now) {
		QVehicle veh;
		// handle transit traffic in stop queue
		List<QVehicle> departingTransitVehicles = null;
		while ((veh = this.transitVehicleStopQueue.peek()) != null) {
			// there is a transit vehicle.
			if (veh.getEarliestLinkExitTime() > now) {
				break;
			}
			if (departingTransitVehicles == null) {
				departingTransitVehicles = new LinkedList<QVehicle>();
			}
			departingTransitVehicles.add(this.transitVehicleStopQueue.poll());
		}
		if (departingTransitVehicles != null) {
			// add all departing transit vehicles at the front of the vehQueue
			ListIterator<QVehicle> iter = departingTransitVehicles.listIterator(departingTransitVehicles.size());
			while (iter.hasPrevious()) {
				this.queueLane.getVehQueue().addFirst(iter.previous());
			}
		}
	}

	public boolean handleMoveLaneToBuffer(final double now, final QVehicle veh,
			final MobsimDriverAgent driver) {
		boolean handled = false;
		// handle transit driver if necessary
		if (driver instanceof TransitDriverAgent) {
			TransitDriverAgent transitDriver = (TransitDriverAgent) veh.getDriver();
			TransitStopFacility stop = transitDriver.getNextTransitStop();
			if ((stop != null) && (stop.getLinkId().equals(this.queueLane.getLink().getId()))) {
				double delay = transitDriver.handleTransitStop(stop, now);
				if (delay > 0.0) {

					veh.setEarliestLinkExitTime(now + delay);
					// (if the vehicle is not removed from the queue in the following lines, then this will effectively block the lane

					if (!stop.getIsBlockingLane()) {
						this.queueLane.getVehQueue().poll(); // remove the bus from the queue
						this.transitVehicleStopQueue.add(veh); // and add it to the stop queue
					}
				}
				/* start over: either this veh is still first in line,
				 * but has another stop on this link, or on another link, then it is moved on
				 */
				handled = true;
			}
		}
		return handled;
	}

	public boolean handleMoveWaitToBuffer(final double now, final QVehicle veh) {
		if (veh.getDriver() instanceof TransitDriverAgent) {
			TransitDriverAgent driver = (TransitDriverAgent) veh.getDriver();
			while (true) {
				TransitStopFacility stop = driver.getNextTransitStop();
				if ((stop != null) && (stop.getLinkId().equals(this.queueLane.getLink().getId()))) {
					double delay = driver.handleTransitStop(stop, now);
					if (delay > 0.0) {
						veh.setEarliestLinkExitTime(now + delay);
						// add it to the stop queue, can do this as the waitQueue is also non-blocking anyway
						this.transitVehicleStopQueue.add(veh);
						return true;
					}
				} else {
					return false;
				}
			}
		}
		return false;
	}


	/**
	 * Put the transit vehicles from the transit stop list in positions.
	 */
	public void positionVehiclesFromTransitStop(final Collection<AgentSnapshotInfo> positions, int cnt2 ) {
		if (this.transitVehicleStopQueue.size() > 0) {
//			lane++; // place them one lane further away
//			double vehPosition = queueLane.getLink().getLength();
			for (QVehicle veh : this.transitVehicleStopQueue) {
////				PositionInfo position = new PositionInfo(OTFDefaultLinkHandler.LINK_SCALE, veh.getDriver().getPerson().getId(), queueLane.getLink(),
////						vehPosition, lane, 0.0, 	AgentSnapshotInfo.AgentState.TRANSIT_DRIVER);
//				AgentSnapshotInfo position = new PositionInfo( veh.getDriver().getPerson().getId(), queueLane.getLink(), cnt2 ) ;
//				position.setAgentState( AgentState.TRANSIT_DRIVER ) ;
//				positions.add(position);
////				vehPosition -= veh.getSizeInEquivalents() * cellSize;
//				// also add the passengers (so we can track them)? kai, apr'10
				// This is now done (see below). kai, sep'10

				List<MobsimAgent> peopleInVehicle = getPassengers(veh);
				boolean last = false ;
				cnt2 += peopleInVehicle.size() ;
//				for (PersonAgent passenger : peopleInVehicle) {
				for ( ListIterator<MobsimAgent> it = peopleInVehicle.listIterator( peopleInVehicle.size() ) ; it.hasPrevious(); ) {
					// this now goes backwards through the list so that the sequence is consistent with that of the moving transit vehicle.
					// yyyy Not so great that the "plot passenger" functionality is at two different places. kai, sep'10
					MobsimAgent passenger = it.previous();
					if ( !it.hasPrevious() ) {
						last = true ;
					}
					AgentSnapshotInfo passengerPosition = AgentSnapshotInfoFactory.staticCreateAgentSnapshotInfo(passenger.getId(), this.queueLane.getLink(), cnt2); // for the time being, same position as facilities
					if ( passenger.getId().toString().startsWith("pt")) {
						passengerPosition.setAgentState(AgentState.TRANSIT_DRIVER);
					} else if (last) {
						passengerPosition.setAgentState(AgentState.PERSON_DRIVING_CAR);
					} else {
						passengerPosition.setAgentState(AgentState.PERSON_OTHER_MODE);
					}
					positions.add(passengerPosition);
					cnt2-- ;
				}

			}

		}
	}

	public List<MobsimAgent> getPassengers(final QVehicle queueVehicle) {
		// yyyy warum macht diese Methode Sinn?  TransitVehicle.getPassengers() gibt doch
		// bereits eine Collection<PersonAgent> zurück.  Dann braucht das Umkopieren hier doch
		// bloss Zeit?  kai, feb'10
			if (queueVehicle instanceof TransitVehicle) {
				List<MobsimAgent> passengers = new ArrayList<MobsimAgent>();
				for (PassengerAgent passenger : ((TransitVehicle) queueVehicle).getPassengers()) {
					passengers.add((MobsimAgent) passenger);
				}
				return passengers;
			} else {
				return Collections.emptyList();
		}
	}

}
