/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package playground.andreas.P2.replanning.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import playground.andreas.P2.operator.Cooperative;
import playground.andreas.P2.replanning.AbstractPStrategyModule;
import playground.andreas.P2.replanning.PPlan;
import playground.andreas.P2.scoring.fare.FareContainer;
import playground.andreas.P2.scoring.fare.FareContainerHandler;
import playground.andreas.utils.stats.RecursiveStatsContainer;

/**
 * 
 * Restricts the time of operation to temporal relations higher than a certain threshold.
 * Threshold is standard deviation of number of trips of all relations twice a scaling factor.
 * based on route and fare
 * 
 * @author aneumann
 *
 */
public class ReduceTimeServedRFare extends AbstractPStrategyModule implements FareContainerHandler{
	
	private final static Logger log = Logger.getLogger(ReduceTimeServedRFare.class);
	
	public static final String STRATEGY_NAME = "ReduceTimeServedRFare";
	private final double sigmaScale;
	private final int timeBinSize;
	private final boolean useFareAsWeight;
	
	private HashMap<Id,HashMap<Integer,HashMap<Integer,Double>>> route2StartTimeSlot2EndTimeSlot2WeightMap = new HashMap<Id, HashMap<Integer,HashMap<Integer,Double>>>();
	
	public ReduceTimeServedRFare(ArrayList<String> parameter) {
		super(parameter);
		if(parameter.size() != 3){
			log.error("Too many parameter. Will ignore: " + parameter);
			log.error("Parameter 1: Scaling factor for sigma");
			log.error("Parameter 2: Time bin size in seconds");
			log.error("Parameter 3: true=use the fare as weight, false=use number of trips as weight");
		}
		this.sigmaScale = Double.parseDouble(parameter.get(0));
		this.timeBinSize = Integer.parseInt(parameter.get(1));
		this.useFareAsWeight = Boolean.parseBoolean(parameter.get(2));
		log.info("enabled");
	}
	
	@Override
	public PPlan run(Cooperative cooperative) {
		
		if (cooperative.getBestPlan().getNVehicles() <= 1) {
			return null;
		}
		
		// get best plans route id
		TransitRoute routeToOptimize = null;
		if (cooperative.getBestPlan().getLine().getRoutes().size() != 1) {
			log.error("There should be only one route at this time - Please check");
		}
		for (TransitRoute route : cooperative.getBestPlan().getLine().getRoutes().values()) {
			routeToOptimize = route;
		}
		
		Tuple<Double,Double> timeToBeServed = getTimeToBeServed(this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeToOptimize.getId()));
		
		if (timeToBeServed == null) {
			// Could not modify plan
			return null;
		}
		
		// profitable route, change startTime
		PPlan newPlan = new PPlan(new IdImpl(cooperative.getCurrentIteration()), this.getName());
		newPlan.setStopsToBeServed(cooperative.getBestPlan().getStopsToBeServed());
		
		newPlan.setStartTime(timeToBeServed.getFirst());
		newPlan.setEndTime(timeToBeServed.getSecond());
		
		newPlan.setLine(cooperative.getRouteProvider().createTransitLine(cooperative.getId(), newPlan.getStartTime(), newPlan.getEndTime(), 1, newPlan.getStopsToBeServed(), new IdImpl(cooperative.getCurrentIteration())));
		
		return newPlan;
	}


	private Tuple<Double,Double> getTimeToBeServed(HashMap<Integer,HashMap<Integer,Double>> startSlot2EndSlot2TripsMap) {
		RecursiveStatsContainer stats = new RecursiveStatsContainer();
		
		if (startSlot2EndSlot2TripsMap == null) {
			// There is no entry for that particular line - possibly no demand - returning empty line
			return null;
		}
		
		// calculate standard deviation
		for (HashMap<Integer, Double> EndSlot2TripsMap : startSlot2EndSlot2TripsMap.values()) {
			for (Double trips : EndSlot2TripsMap.values()) {
				stats.handleNewEntry(trips.doubleValue());
			}
		}
		
		if (stats.getNumberOfEntries() == 1) {
			// We use circular routes. There is always a way back (with no demand). Add a second entry.
			stats.handleNewEntry(0.0);			
		}
		
		double sigmaTreshold = stats.getStdDev() * this.sigmaScale;
		Set<Integer> slotsAboveTreshold = new TreeSet<Integer>();
		
		// Get all slots serving a demand above threshold
		for (Entry<Integer, HashMap<Integer, Double>> endSlot2TripsMapEntry : startSlot2EndSlot2TripsMap.entrySet()) {
			for (Entry<Integer, Double> tripEntry : endSlot2TripsMapEntry.getValue().entrySet()) {
				if (tripEntry.getValue().doubleValue() > sigmaTreshold) {
					// ok - add the corresponding slots to the set
					slotsAboveTreshold.add(endSlot2TripsMapEntry.getKey());
					slotsAboveTreshold.add(tripEntry.getKey());
				}
			}
		}
		
		// Get new slots to be served
		double min = Integer.MAX_VALUE;
		double max = Integer.MIN_VALUE;
		
		for (Integer slot : slotsAboveTreshold) {
			if (slot.doubleValue() < min) {
				min = slot.doubleValue();
			}
			if (slot.doubleValue() > max) {
				max = slot.doubleValue();
			}
		}
		
		// convert slots to time
		min = min * this.timeBinSize;
		max = (max + 1) * this.timeBinSize;
		
		Tuple<Double, Double> timeSlotsOfOperation = new Tuple<Double, Double>(min, max);
		return timeSlotsOfOperation;
	}

	@Override
	public String getName() {
		return ReduceTimeServedRFare.STRATEGY_NAME;
	}
	
	@Override
	public void reset(int iteration) {
		this.route2StartTimeSlot2EndTimeSlot2WeightMap = new HashMap<Id, HashMap<Integer,HashMap<Integer,Double>>>();
	}

	@Override
	public void handleFareContainer(FareContainer fareContainer) {
		Id routeId = fareContainer.getRouteId();
		Integer startTimeSlot = this.getTimeSlotForTime(fareContainer.getTimeEntered());
		Integer endTimeSlot = this.getTimeSlotForTime(fareContainer.getTimeLeft());
		
		if (this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeId) == null) {
			this.route2StartTimeSlot2EndTimeSlot2WeightMap.put(routeId, new HashMap<Integer, HashMap<Integer,Double>>());
		}
	
		if (this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeId).get(startTimeSlot) == null) {
			this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeId).put(startTimeSlot, new HashMap<Integer,Double>());
		}
	
		if (this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeId).get(startTimeSlot).get(endTimeSlot) == null) {
			this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeId).get(startTimeSlot).put(endTimeSlot, new Double(0));
		}
	
		double oldWeight = this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeId).get(startTimeSlot).get(endTimeSlot);
		double additionalWeight = 1.0;
		if (this.useFareAsWeight) {
			additionalWeight = fareContainer.getFare();
		}
		this.route2StartTimeSlot2EndTimeSlot2WeightMap.get(routeId).get(startTimeSlot).put(endTimeSlot, new Double(oldWeight + additionalWeight));
	}

	private int getTimeSlotForTime(double time){
		return ((int) time / this.timeBinSize);
	}
}