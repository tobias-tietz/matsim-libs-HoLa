/* *********************************************************************** *
 * project: org.matsim.*
 * QueueSimulationTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2009 by the members listed in the COPYING,  *
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

package org.matsim.core.mobsim.queuesim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.SimulationConfigGroup;
import org.matsim.core.events.ActivityEndEventImpl;
import org.matsim.core.events.ActivityStartEventImpl;
import org.matsim.core.events.AgentArrivalEventImpl;
import org.matsim.core.events.AgentDepartureEventImpl;
import org.matsim.core.events.AgentWait2LinkEventImpl;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.LinkEnterEventImpl;
import org.matsim.core.events.LinkLeaveEventImpl;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.routes.GenericRoute;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.ConfigUtils;
import org.matsim.core.utils.misc.NetworkUtils;
import org.matsim.ptproject.qsim.qnetsimengine.QVehicle;
import org.matsim.testcases.MatsimTestCase;
import org.matsim.testcases.utils.EventsCollector;
import org.matsim.testcases.utils.LogCounter;
import org.matsim.vehicles.VehicleImpl;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleTypeImpl;
import org.matsim.vis.snapshots.writers.VisVehicle;

public class QueueSimulationTest extends TestCase {

	private final static Logger log = Logger.getLogger(QueueSimulationTest.class);

	/**
	 * This test is mostly useful for manual debugging, because only a single agent is simulated
	 * on a very simple network.
	 *
	 * @author mrieser
	 */
	public void testSingleAgent() {
		Fixture f = new Fixture();

		// add a single person with leg from link1 to link3
		PersonImpl person = new PersonImpl(new IdImpl(0));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(6*3600);
		Leg leg = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link3.getId());
		route.setLinkIds(f.link1.getId(), f.linkIds2, f.link3.getId());
		leg.setRoute(route);
		plan.createAndAddActivity("w", f.link3.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		LinkEnterEventCollector collector = new LinkEnterEventCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		assertEquals("wrong number of link enter events.", 2, collector.events.size());
		assertEquals("wrong time in first event.", 6.0*3600 + 1, collector.events.get(0).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in second event.", 6.0*3600 + 12, collector.events.get(1).getTime(), MatsimTestCase.EPSILON);
	}

	/**
	 * This test is mostly useful for manual debugging, because only two single agents are simulated
	 * on a very simple network.
	 *
	 * @author mrieser
	 */
	public void testTwoAgent() {
		Fixture f = new Fixture();

		// add two persons with leg from link1 to link3, the first starting at 6am, the second at 7am
		for (int i = 0; i < 2; i++) {
			PersonImpl person = new PersonImpl(new IdImpl(i));
			PlanImpl plan = person.createAndAddPlan(true);
			ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
			a1.setEndTime((6+i)*3600);
			Leg leg = plan.createAndAddLeg(TransportMode.car);
			NetworkRoute route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link3.getId());
			route.setLinkIds(f.link1.getId(), f.linkIds2, f.link3.getId());
			leg.setRoute(route);
			plan.createAndAddActivity("w", f.link3.getId());
			f.plans.addPerson(person);
		}

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		LinkEnterEventCollector collector = new LinkEnterEventCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		assertEquals("wrong number of link enter events.", 4, collector.events.size());
		assertEquals("wrong time in first event.", 6.0*3600 + 1, collector.events.get(0).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in second event.", 6.0*3600 + 12, collector.events.get(1).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in first event.", 7.0*3600 + 1, collector.events.get(2).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in second event.", 7.0*3600 + 12, collector.events.get(3).getTime(), MatsimTestCase.EPSILON);
	}

	/**
	 * A single agent is simulated that uses teleportation for its one and only leg.
	 *
	 * @author mrieser
	 */
	public void testTeleportationSingleAgent() {
		Fixture f = new Fixture();

		// add a single person with leg from link1 to link3
		PersonImpl person = new PersonImpl(new IdImpl(0));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(6*3600);
		Leg leg = plan.createAndAddLeg("other");
		Route route = f.network.getFactory().createRoute("undefined", f.link1.getId(), f.link3.getId());
		leg.setRoute(route);
		leg.setTravelTime(15.0);
		plan.createAndAddActivity("w", f.link3.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		EventsCollector collector = new EventsCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		List<Event> allEvents = collector.getEvents();
		assertEquals("wrong number of events.", 4, collector.getEvents().size());
		assertEquals("wrong type of 1st event.", ActivityEndEventImpl.class, allEvents.get(0).getClass());
		assertEquals("wrong type of 2nd event.", AgentDepartureEventImpl.class, allEvents.get(1).getClass());
		assertEquals("wrong type of 3rd event.", AgentArrivalEventImpl.class, allEvents.get(2).getClass());
		assertEquals("wrong type of 4th event.", ActivityStartEventImpl.class, allEvents.get(3).getClass());
		assertEquals("wrong time in 1st event.", 6.0*3600 + 0, allEvents.get(0).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in 2nd event.", 6.0*3600 + 0, allEvents.get(1).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in 3rd event.", 6.0*3600 + 15, allEvents.get(2).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in 4th event.", 6.0*3600 + 15, allEvents.get(3).getTime(), MatsimTestCase.EPSILON);
	}

	/**
	 * Simulates a single agent that has two activities on the same link. Tests if the simulation
	 * correctly recognizes such cases.
	 *
	 * @author mrieser
	 */
	public void testSingleAgent_EmptyRoute() {
		Fixture f = new Fixture();

		// add a single person with leg from link1 to link3
		PersonImpl person = new PersonImpl(new IdImpl(0));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(6*3600);
		Leg leg = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link1.getId());
		route.setLinkIds(f.link1.getId(), f.linkIdsNone, f.link1.getId());
		leg.setRoute(route);
		plan.createAndAddActivity("w", f.link1.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		EventsCollector collector = new EventsCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		List<Event> allEvents = collector.getEvents();
		assertEquals("wrong number of events.", 4, allEvents.size());
		assertEquals("wrong type of 1st event.", ActivityEndEventImpl.class, allEvents.get(0).getClass());
		assertEquals("wrong type of 2nd event.", AgentDepartureEventImpl.class, allEvents.get(1).getClass());
		assertEquals("wrong type of 3rd event.", AgentArrivalEventImpl.class, allEvents.get(2).getClass());
		assertEquals("wrong type of 4th event.", ActivityStartEventImpl.class, allEvents.get(3).getClass());
		assertEquals("wrong time in 1st event.", 6.0*3600 + 0, allEvents.get(0).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in 2nd event.", 6.0*3600 + 0, allEvents.get(1).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in 3rd event.", 6.0*3600 + 0, allEvents.get(2).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong time in 4th event.", 6.0*3600 + 0, allEvents.get(3).getTime(), MatsimTestCase.EPSILON);
		assertEquals("wrong link in 1st event.", f.link1.getId(), ((ActivityEndEventImpl) allEvents.get(0)).getLinkId());
		assertEquals("wrong link in 2nd event.", f.link1.getId(), ((AgentDepartureEventImpl) allEvents.get(1)).getLinkId());
		assertEquals("wrong link in 3rd event.", f.link1.getId(), ((AgentArrivalEventImpl) allEvents.get(2)).getLinkId());
		assertEquals("wrong link in 4th event.", f.link1.getId(), ((ActivityStartEventImpl) allEvents.get(3)).getLinkId());
	}

	/*package*/ static class LinkEnterEventCollector implements LinkEnterEventHandler {
		public final ArrayList<LinkEnterEvent> events = new ArrayList<LinkEnterEvent>();
		@Override
		public void handleEvent(final LinkEnterEvent event) {
			this.events.add(event);
		}
		@Override
		public void reset(final int iteration) {
			this.events.clear();
		}
	}

	/**
	 * Tests that no Exception occurs if an agent has no leg at all.
	 *
	 * @author mrieser
	 */
	public void testAgentWithoutLeg() {
		Fixture f = new Fixture();

		PersonImpl person = new PersonImpl(new IdImpl(1));
		PlanImpl plan = person.createAndAddPlan(true);
		plan.createAndAddActivity("home", f.link1.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		LinkEnterEventCollector collector = new LinkEnterEventCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		assertEquals("wrong number of link enter events.", 0, collector.events.size());
	}

	/**
	 * Tests that no Exception occurs if an agent has no leg at all, but the only activity has an end time set.
	 *
	 * @author mrieser
	 */
	public void testAgentWithoutLegWithEndtime() {
		Fixture f = new Fixture();

		PersonImpl person = new PersonImpl(new IdImpl(1));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl act = plan.createAndAddActivity("home", f.link1.getId());
		act.setEndTime(6.0 * 3600);
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		LinkEnterEventCollector collector = new LinkEnterEventCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		assertEquals("wrong number of link enter events.", 0, collector.events.size());
	}

	/**
	 * Tests that no Exception occurs if the last activity of an agent has an end time set (which is wrong).
	 *
	 * @author mrieser
	 */
	public void testAgentWithLastActWithEndtime() {
		Fixture f = new Fixture();

		PersonImpl person = new PersonImpl(new IdImpl(1));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl act = plan.createAndAddActivity("home", f.link1.getId());
		act.setEndTime(6.0 * 3600);
		Leg leg = plan.createAndAddLeg(TransportMode.walk);
		leg.setRoute(new GenericRouteImpl(f.link1.getId(), f.link2.getId()));
		act = plan.createAndAddActivity("work", f.link2.getId());
		act.setEndTime(6.0 * 3600 + 60);
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		LinkEnterEventCollector collector = new LinkEnterEventCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		assertEquals("wrong number of link enter events.", 0, collector.events.size());
	}

	/**
	 * Tests that the flow capacity can be reached (but not exceeded) by
	 * agents driving over a link.
	 *
	 * @author mrieser
	 */
	public void testFlowCapacityDriving() {
		Fixture f = new Fixture();

		// add a first person with leg from link1 to link3, let it start early, so the simulation can accumulate buffer capacity
		PersonImpl person = new PersonImpl(new IdImpl(0));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(6*3600 - 500);
		Leg leg = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link3.getId());
		route.setLinkIds(f.link1.getId(), f.linkIds2, f.link3.getId());
		leg.setRoute(route);
		plan.createAndAddActivity("w", f.link3.getId());
		f.plans.addPerson(person);

		// add a lot of other persons with legs from link1 to link3, starting at 6:30
		for (int i = 1; i <= 10000; i++) {
			person = new PersonImpl(new IdImpl(i));
			plan = person.createAndAddPlan(true);
			/* exact dep. time: 6:29:48. The agents needs:
			 * - at the specified time, the agent goes into the waiting list, and if space is available, into
			 * the buffer of link 1.
			 * - 1 sec later, it leaves the buffer on link 1 and enters link 2
			 * - the agent takes 10 sec. to travel along link 2, after which it gets placed in the buffer of link 2
			 * - 1 sec later, the agent leaves the buffer on link 2 (if flow-cap allows this) and enters link 3
			 * - as we measure the vehicles leaving link 2, and the first veh should leave at exactly 6:30, it has
			 * to start 1 + 10 + 1 = 12 secs earlier.
			 * So, the start time is 7*3600 - 1800 - 12 = 7*3600 - 1812
			 */
			ActivityImpl a = plan.createAndAddActivity("h", f.link1.getId());
			a.setEndTime(7*3600 - 1812);
			leg = plan.createAndAddLeg(TransportMode.car);
			route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link3.getId());
			route.setLinkIds(f.link1.getId(), f.linkIds2, f.link3.getId());
			leg.setRoute(route);
			plan.createAndAddActivity("w", f.link3.getId());
			f.plans.addPerson(person);
		}

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		VolumesAnalyzer vAnalyzer = new VolumesAnalyzer(3600, 9*3600, f.network);
		events.addHandler(vAnalyzer);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		int[] volume = vAnalyzer.getVolumesForLink(f.link2.getId());
		System.out.println("#vehicles 6-7: " + Integer.toString(volume[6]));
		System.out.println("#vehicles 7-8: " + Integer.toString(volume[7]));
		System.out.println("#vehicles 8-9: " + Integer.toString(volume[8]));

		assertEquals(3000, volume[6]); // we should have half of the maximum flow in this hour
		assertEquals(6000, volume[7]); // we should have maximum flow in this hour
		assertEquals(1000, volume[8]); // all the rest
	}

	/**
	 * Tests that the flow capacity can be reached (but not exceeded) by
	 * agents starting on a link. Due to the different handling of these
	 * agents and their direct placing in the Buffer, it makes sense to
	 * test this specifically.
	 *
	 * @author mrieser
	 */
	public void testFlowCapacityStarting() {
		Fixture f = new Fixture();

		// add a first person with leg from link1 to link3, let it start early, so the simulation can accumulate buffer capacity
		PersonImpl person = new PersonImpl(new IdImpl(0));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(6*3600 - 500);
		Leg leg = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link3.getId());
		route.setLinkIds(f.link1.getId(), f.linkIds2, f.link3.getId());
		leg.setRoute(route);
		plan.createAndAddActivity("w", f.link3.getId());
		f.plans.addPerson(person);

		// add a lot of persons with legs from link2 to link3
		for (int i = 1; i <= 10000; i++) {
			person = new PersonImpl(new IdImpl(i));
			plan = person.createAndAddPlan(true);
			ActivityImpl a2 = plan.createAndAddActivity("h", f.link2.getId());
			a2.setEndTime(7*3600 - 1801);
			leg = plan.createAndAddLeg(TransportMode.car);
			route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link2.getId(), f.link3.getId());
			route.setLinkIds(f.link2.getId(), f.linkIdsNone, f.link3.getId());
			leg.setRoute(route);
			plan.createAndAddActivity("w", f.link3.getId());
			f.plans.addPerson(person);
		}

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		VolumesAnalyzer vAnalyzer = new VolumesAnalyzer(3600, 9*3600, f.network);
		events.addHandler(vAnalyzer);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		int[] volume = vAnalyzer.getVolumesForLink(f.link2.getId());
		System.out.println("#vehicles 6-7: " + Integer.toString(volume[6]));
		System.out.println("#vehicles 7-8: " + Integer.toString(volume[7]));
		System.out.println("#vehicles 8-9: " + Integer.toString(volume[8]));

		assertEquals(3000, volume[6]); // we should have half of the maximum flow in this hour
		assertEquals(6000, volume[7]); // we should have maximum flow in this hour
		assertEquals(1000, volume[8]); // all the rest
	}

	/**
	 * Tests that the flow capacity of a link can be reached (but not exceeded) by
	 * agents starting on that link or driving through that link. This especially
	 * insures that the flow capacity measures both kinds (starting, driving) together.
	 *
	 * @author mrieser
	 */
	public void testFlowCapacityMixed() {
		Fixture f = new Fixture();

		// add a first person with leg from link1 to link3, let it start early, so the simulation can accumulate buffer capacity
		PersonImpl person = new PersonImpl(new IdImpl(0));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(6*3600 - 500);
		Leg leg = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link3.getId());
		route.setLinkIds(f.link1.getId(), f.linkIds2, f.link3.getId());
		leg.setRoute(route);
		plan.createAndAddActivity("w", f.link3.getId());
		f.plans.addPerson(person);

		// add a lot of persons with legs from link2 to link3
		for (int i = 1; i <= 5000; i++) {
			person = new PersonImpl(new IdImpl(i));
			plan = person.createAndAddPlan(true);
			ActivityImpl a2 = plan.createAndAddActivity("h", f.link2.getId());
			a2.setEndTime(7*3600 - 1801);
			leg = plan.createAndAddLeg(TransportMode.car);
			route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link2.getId(), f.link3.getId());
			route.setLinkIds(f.link2.getId(), f.linkIdsNone, f.link3.getId());
			leg.setRoute(route);
			plan.createAndAddActivity("w", f.link3.getId());
			f.plans.addPerson(person);
		}
		// add a lot of persons with legs from link1 to link3
		for (int i = 5001; i <= 10000; i++) {
			person = new PersonImpl(new IdImpl(i));
			plan = person.createAndAddPlan(true);
			ActivityImpl a2 = plan.createAndAddActivity("h", f.link1.getId());
			a2.setEndTime(7*3600 - 1812);
			leg = plan.createAndAddLeg(TransportMode.car);
			route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link2.getId(), f.link3.getId());
			route.setLinkIds(f.link1.getId(), f.linkIds2, f.link3.getId());
			leg.setRoute(route);
			plan.createAndAddActivity("w", f.link3.getId());
			f.plans.addPerson(person);
		}

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		VolumesAnalyzer vAnalyzer = new VolumesAnalyzer(3600, 9*3600, f.network);
		events.addHandler(vAnalyzer);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		int[] volume = vAnalyzer.getVolumesForLink(f.link2.getId());
		System.out.println("#vehicles 6-7: " + Integer.toString(volume[6]));
		System.out.println("#vehicles 7-8: " + Integer.toString(volume[7]));
		System.out.println("#vehicles 8-9: " + Integer.toString(volume[8]));

		assertEquals(3000, volume[6]); // we should have half of the maximum flow in this hour
		assertEquals(6000, volume[7]); // we should have maximum flow in this hour
		assertEquals(1000, volume[8]); // all the rest
	}

	/**
	 * Tests that vehicles are teleported if needed so that agents can use the car wherever they want.
	 *
	 * @author mrieser
	 */
	public void testVehicleTeleportationTrue() {
		Fixture f = new Fixture();
		PersonImpl person = new PersonImpl(new IdImpl(1));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(7.0*3600);
		Leg l1 = plan.createAndAddLeg("other");
		l1.setTravelTime(10);
		l1.setRoute(f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link2.getId()));
		ActivityImpl a2 = plan.createAndAddActivity("w", f.link2.getId());
		a2.setEndTime(7.0*3600 + 20);
		Leg l2 = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route2 = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link2.getId(), f.link3.getId());
		route2.setLinkIds(f.link2.getId(), f.linkIdsNone, f.link3.getId());
		l2.setRoute(route2);
		plan.createAndAddActivity("l", f.link3.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		EventsCollector collector = new EventsCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.setTeleportVehicles(true);
		sim.run();

		/* finish */
		List<Event> allEvents = collector.getEvents();
		assertEquals("wrong number of events.", 11, allEvents.size());
		assertEquals("wrong type of event.", ActivityEndEventImpl.class, allEvents.get(0).getClass());
		assertEquals("wrong type of event.", AgentDepartureEventImpl.class, allEvents.get(1).getClass());
		assertEquals("wrong type of event.", AgentArrivalEventImpl.class, allEvents.get(2).getClass());
		assertEquals("wrong type of event.", ActivityStartEventImpl.class, allEvents.get(3).getClass());
		assertEquals("wrong type of event.", ActivityEndEventImpl.class, allEvents.get(4).getClass());
		assertEquals("wrong type of event.", AgentDepartureEventImpl.class, allEvents.get(5).getClass());
		assertEquals("wrong type of event.", AgentWait2LinkEventImpl.class, allEvents.get(6).getClass());
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(7).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(8).getClass());
		assertEquals("wrong type of event.", AgentArrivalEventImpl.class, allEvents.get(9).getClass());
		assertEquals("wrong type of event.", ActivityStartEventImpl.class, allEvents.get(10).getClass());
	}

	/**
	 * Tests that vehicles are not teleported if they are missing, but that an Exception is thrown instead.
	 *
	 * @author mrieser
	 */
	public void testVehicleTeleportationFalse() {
		Fixture f = new Fixture();
		PersonImpl person = new PersonImpl(new IdImpl(1));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(7.0*3600);
		Leg l1 = plan.createAndAddLeg("other");
		l1.setTravelTime(10);
		l1.setRoute(f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link2.getId())); // TODO [MR] use different factory / TransportationMode
		ActivityImpl a2 = plan.createAndAddActivity("w", f.link2.getId());
		a2.setEndTime(7.0*3600 + 20);
		Leg l2 = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route2 = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link2.getId(), f.link3.getId());
		route2.setLinkIds(f.link2.getId(), f.linkIdsNone, f.link3.getId());
		l2.setRoute(route2);
		plan.createAndAddActivity("l", f.link3.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		EventsCollector collector = new EventsCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.setTeleportVehicles(false);
		try {
			sim.run();
			fail("expected RuntimeException, but there was none.");
		} catch (RuntimeException e) {
			log.info("catched expected RuntimeException: " + e.getMessage());
		}

		/* finish */
		List<Event> allEvents = collector.getEvents();
		assertEquals("wrong number of events.", 6, allEvents.size());
		assertEquals("wrong type of event.", ActivityEndEventImpl.class, allEvents.get(0).getClass());
		assertEquals("wrong type of event.", AgentDepartureEventImpl.class, allEvents.get(1).getClass());
		assertEquals("wrong type of event.", AgentArrivalEventImpl.class, allEvents.get(2).getClass());
		assertEquals("wrong type of event.", ActivityStartEventImpl.class, allEvents.get(3).getClass());
		assertEquals("wrong type of event.", ActivityEndEventImpl.class, allEvents.get(4).getClass());
		assertEquals("wrong type of event.", AgentDepartureEventImpl.class, allEvents.get(5).getClass());
	}

	/**
	 * Tests that if a specific vehicle is assigned to an agent in its NetworkRoute, that this vehicle
	 * is used instead of a default one.
	 *
	 * @author mrieser
	 */
	public void testAssignedVehicles() {
		Fixture f = new Fixture();
		Id id1 = new IdImpl(1);
		Id id2 = new IdImpl(2);
		PersonImpl person = new PersonImpl(id1); // do not add person to population, we'll do it ourselves for the test
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link2.getId());
		a1.setEndTime(7.0*3600);
		Leg l1 = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route1 = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link2.getId(), f.link3.getId());
		route1.setLinkIds(f.link2.getId(), f.linkIdsNone, f.link3.getId());
		route1.setVehicleId(id2);
		l1.setRoute(route1);
		plan.createAndAddActivity("w", f.link3.getId());

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();

		/* prepare sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		QueueNetwork qnet = sim.getQueueNetwork();
		QueueLink qlink2 = qnet.getQueueLink(id2);
		QueueLink qlink3 = qnet.getQueueLink(new IdImpl(3));

		VehicleType defaultVehicleType = new VehicleTypeImpl(new IdImpl("defaultVehicleType"));
		QVehicle vehicle1 = StaticFactoriesContainer.createQueueVehicle(new VehicleImpl(id1, defaultVehicleType));
		QVehicle vehicle2 = StaticFactoriesContainer.createQueueVehicle(new VehicleImpl(id2, defaultVehicleType));
		qlink2.addParkedVehicle(vehicle1);
		qlink2.addParkedVehicle(vehicle2);

		sim.getSimTimer().setTime(100.0);
		MobsimDriverAgent agent = StaticFactoriesContainer.createQueuePersonAgent(person, sim);
		agent.initialize();
		agent.endActivityAndAssumeControl(100.0);

		sim.getSimTimer().setTime(101.0);
		sim.doSimStep(101.0); // agent should be moved to qlink2.buffer
		sim.getSimTimer().setTime(102.0);
		sim.doSimStep(102.0); // agent should be moved to qlink3

		Collection<? extends VisVehicle> vehicles = qlink3.getAllVehicles();
		assertEquals(1, vehicles.size());
//		assertEquals(id2, vehicles.toArray(new QueueVehicle[1])[0].getBasicVehicle().getId());
		assertEquals(id2, vehicles.iterator().next().getVehicle().getId());
		// vehicle 1 should still stay on qlink2
		vehicles = qlink2.getAllVehicles();
		assertEquals(1, vehicles.size());
//		assertEquals(id1, vehicles.toArray(new QueueVehicle[1])[0].getBasicVehicle().getId());
		assertEquals(id1, vehicles.iterator().next().getVehicle().getId());
	}

	/**
	 * Tests that a vehicle starts its route even when start and end link are the same.
	 *
	 * @author mrieser
	 */
	public void testCircleAsRoute() {
		Fixture f = new Fixture();
		Link link4 = f.network.getFactory().createLink(new IdImpl(4), f.node4, f.node1); // close the network
		link4.setLength(1000.0);
		link4.setFreespeed(100.0);
		link4.setCapacity(6000);
		link4.setNumberOfLanes(1);
		f.network.addLink(link4);

		PersonImpl person = new PersonImpl(new IdImpl(1));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(7.0*3600);
		Leg l1 = plan.createAndAddLeg(TransportMode.car);
		l1.setTravelTime(10);
		NetworkRoute netRoute = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link1.getId());
		List<Id> routeLinks = new ArrayList<Id>();
		Collections.addAll(routeLinks, f.link2.getId(), f.link3.getId(), link4.getId());
		netRoute.setLinkIds(f.link1.getId(), routeLinks, f.link1.getId());
		l1.setRoute(netRoute);

		plan.createAndAddActivity("w", f.link1.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		EventsCollector collector = new EventsCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		List<Event> allEvents = collector.getEvents();
		assertEquals("wrong number of events.", 13, allEvents.size());
		assertEquals("wrong type of event.", ActivityEndEventImpl.class, allEvents.get(0).getClass());
		assertEquals("wrong type of event.", AgentDepartureEventImpl.class, allEvents.get(1).getClass());
		assertEquals("wrong type of event.", AgentWait2LinkEventImpl.class, allEvents.get(2).getClass());
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(3).getClass()); // link1
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(4).getClass()); // link2
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(5).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(6).getClass()); // link3
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(7).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(8).getClass()); // link4
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(9).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(10).getClass()); // link1 again
		assertEquals("wrong type of event.", AgentArrivalEventImpl.class, allEvents.get(11).getClass());
		assertEquals("wrong type of event.", ActivityStartEventImpl.class, allEvents.get(12).getClass());
	}

	/**
	 * Tests that if the endLink of a route is contained within the route itself,
	 * the vehicle really drives until the end of its route and is not stopped
	 * when it reaches the endLink the first time.
	 *
	 * @author mrieser
	 */
	public void testRouteWithEndLinkTwice() {
		Fixture f = new Fixture();
		Link link4 = f.network.getFactory().createLink(new IdImpl(4), f.node4, f.node1); // close the network
		link4.setLength(1000.0);
		link4.setFreespeed(100.0);
		link4.setCapacity(6000);
		link4.setNumberOfLanes(1);
		f.network.addLink(link4);

		PersonImpl person = new PersonImpl(new IdImpl(1));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(7.0*3600);
		Leg l1 = plan.createAndAddLeg(TransportMode.car);
		l1.setTravelTime(10);
		NetworkRoute netRoute = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, f.link1.getId(), f.link3.getId());
		List<Id> routeLinks = new ArrayList<Id>();
		Collections.addAll(routeLinks, f.link2.getId(), f.link3.getId(), link4.getId(), f.link1.getId(), f.link2.getId());
		netRoute.setLinkIds(f.link1.getId(), routeLinks, f.link3.getId());
		l1.setRoute(netRoute);

		plan.createAndAddActivity("w", f.link3.getId());
		f.plans.addPerson(person);

		/* build events */
		EventsManager events = EventsUtils.createEventsManager();
		EventsCollector collector = new EventsCollector();
		events.addHandler(collector);

		/* run sim */
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(f.scenario, events);
		sim.run();

		/* finish */
		List<Event> allEvents = collector.getEvents();
		assertEquals("wrong number of events.", 17, allEvents.size());
		assertEquals("wrong type of event.", ActivityEndEventImpl.class, allEvents.get(0).getClass());
		assertEquals("wrong type of event.", AgentDepartureEventImpl.class, allEvents.get(1).getClass());
		assertEquals("wrong type of event.", AgentWait2LinkEventImpl.class, allEvents.get(2).getClass());
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(3).getClass()); // link1
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(4).getClass()); // link2
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(5).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(6).getClass()); // link3
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(7).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(8).getClass()); // link4
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(9).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(10).getClass()); // link1 again
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(11).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(12).getClass()); // link2 again
		assertEquals("wrong type of event.", LinkLeaveEventImpl.class, allEvents.get(13).getClass());
		assertEquals("wrong type of event.", LinkEnterEventImpl.class, allEvents.get(14).getClass()); // link3 again
		assertEquals("wrong type of event.", AgentArrivalEventImpl.class, allEvents.get(15).getClass());
		assertEquals("wrong type of event.", ActivityStartEventImpl.class, allEvents.get(16).getClass());
	}

	/**
	 * Tests that the QueueSimulation reports a problem if the route of a vehicle
	 * does not lead to the destination link.
	 *
	 * @author mrieser
	 */
	public void testConsistentRoutes_WrongRoute() {
		EventsManager events = EventsUtils.createEventsManager();
		EnterLinkEventCounter counter = new EnterLinkEventCounter("6");
		events.addHandler(counter);
		LogCounter logger = runConsistentRoutesTestSim("1", "2 3", "5", events); // route should continue on link 4
		assertEquals(0, counter.getCounter()); // the agent should have been removed from the sim, so nobody travels there
		assertTrue((logger.getWarnCount() + logger.getErrorCount()) > 0); // there should have been at least a warning
	}

	/**
	 * Tests that the QueueSimulation reports a problem if the route of a vehicle
	 * starts at another link than the previous activity is located at.
	 *
	 * @author mrieser
	 */
	public void testConsistentRoutes_WrongStartLink() {
		EventsManager events = EventsUtils.createEventsManager();
		EnterLinkEventCounter counter = new EnterLinkEventCounter("6");
		events.addHandler(counter);
		LogCounter logger = runConsistentRoutesTestSim("2", "3 4", "5", events); // first act is on link 1, not 2
		assertEquals(0, counter.getCounter()); // the agent should have been removed from the sim, so nobody travels there
		assertTrue((logger.getWarnCount() + logger.getErrorCount()) > 0); // there should have been at least a warning
	}

	/**
	 * Tests that the QueueSimulation reports a problem if the route of a vehicle
	 * starts at another link than the previous activity is located at.
	 *
	 * @author mrieser
	 */
	public void testConsistentRoutes_WrongEndLink() {
		EventsManager events = EventsUtils.createEventsManager();
		EnterLinkEventCounter counter = new EnterLinkEventCounter("6");
		events.addHandler(counter);
		LogCounter logger = runConsistentRoutesTestSim("1", "2 3", "4", events); // second act is on link 5, not 4
		assertEquals(0, counter.getCounter()); // the agent should have been removed from the sim, so nobody travels there
		assertTrue((logger.getWarnCount() + logger.getErrorCount()) > 0); // there should have been at least a warning
	}

	/**
	 * Tests that the QueueSimulation reports a problem if the route of a vehicle
	 * does not specify all nodes, so it is unclear at one node or another how to
	 * continue.
	 *
	 * @author mrieser
	 */
	public void testConsistentRoutes_ImpossibleRoute() {
		EventsManager events = EventsUtils.createEventsManager();
		EnterLinkEventCounter counter = new EnterLinkEventCounter("6");
		events.addHandler(counter);
		LogCounter logger = runConsistentRoutesTestSim("1", "2 4", "5", events); // link 3 is missing
		assertEquals(0, counter.getCounter()); // the agent should have been removed from the sim, so nobody travels there
		assertTrue((logger.getWarnCount() + logger.getErrorCount()) > 0); // there should have been at least a warning
	}

	/**
	 * Tests that the QueueSimulation reports a problem if the route of a vehicle
	 * is not specified, even that the destination link is different from the departure link.
	 *
	 * @author mrieser
	 */
	public void testConsistentRoutes_MissingRoute() {
		EventsManager events = EventsUtils.createEventsManager();
		EnterLinkEventCounter counter = new EnterLinkEventCounter("6");
		events.addHandler(counter);
		LogCounter logger = runConsistentRoutesTestSim("1", "", "5", events); // no links at all
		assertEquals(0, counter.getCounter()); // the agent should have been removed from the sim, so nobody travels there
		assertTrue((logger.getWarnCount() + logger.getErrorCount()) > 0); // there should have been at least a warning
	}

	/** Prepares miscellaneous data for the testConsistentRoutes() tests:
	 * Creates a network of 6 links, and a population of one person driving from
	 * link 1 to link 5, and then from link 5 to link 6.
	 *
	 * @param startLinkId the start link of the route for the first leg
	 * @param linkIds the links the agent should travel along on the first leg
	 * @param endLinkId the end link of the route for the first leg
	 * @param events the Events object to be used by the simulation.
	 * @return A QueueSimulation which can be started immediately.
	 *
	 * @author mrieser
	 **/
	private LogCounter runConsistentRoutesTestSim(final String startLinkId, final String linkIds, final String endLinkId, final EventsManager events) {
		Fixture f = new Fixture();

		/* enhance network */
		Node node5 = f.network.getFactory().createNode(f.scenario.createId("5"), f.scenario.createCoord(3100, 0));
		Node node6 = f.network.getFactory().createNode(f.scenario.createId("6"), f.scenario.createCoord(3200, 0));
		Node node7 = f.network.getFactory().createNode(f.scenario.createId("7"), f.scenario.createCoord(3300, 0));
		f.network.addNode(node5);
		f.network.addNode(node6);
		f.network.addNode(node7);

		Link link4 = f.network.getFactory().createLink(new IdImpl(4), f.node4, f.node1); // close the network
		link4.setLength(1000.0);
		link4.setFreespeed(100.0);
		link4.setCapacity(6000);
		link4.setNumberOfLanes(1);
		f.network.addLink(link4);

		Link link5 = f.network.getFactory().createLink(new IdImpl(5), f.node4, f.node1); // close the network
		link5.setLength(100.0);
		link5.setFreespeed(10.0);
		link5.setCapacity(60000);
		link5.setNumberOfLanes(9);
		f.network.addLink(link5);

		Link link6 = f.network.getFactory().createLink(new IdImpl(6), f.node4, f.node1); // close the network
		link6.setLength(100.0);
		link6.setFreespeed(10.0);
		link6.setCapacity(60000);
		link6.setNumberOfLanes(9);
		f.network.addLink(link6);

		// create a person with a car-leg from link1 to link5, but an incomplete route
		PersonImpl person = new PersonImpl(new IdImpl(0));
		PlanImpl plan = person.createAndAddPlan(true);
		ActivityImpl a1 = plan.createAndAddActivity("h", f.link1.getId());
		a1.setEndTime(8*3600);
		Leg leg = plan.createAndAddLeg(TransportMode.car);
		NetworkRoute route = new LinkNetworkRouteImpl(f.link1.getId(), link5.getId());
		route.setLinkIds(new IdImpl(startLinkId), NetworkUtils.getLinkIds(linkIds), new IdImpl(endLinkId));
		leg.setRoute(route);
		ActivityImpl a2 = plan.createAndAddActivity("w", link5.getId());
		a2.setEndTime(9*3600);
		leg = plan.createAndAddLeg(TransportMode.car);
		route = (NetworkRoute) f.network.getFactory().createRoute(TransportMode.car, link5.getId(), link6.getId());
		route.setLinkIds(link5.getId(), null, link6.getId());
		leg.setRoute(route);
		plan.createAndAddActivity("h", link6.getId());
		f.plans.addPerson(person);

		/* run sim with special logger */
		LogCounter logger = new LogCounter(Level.WARN);
		Logger.getRootLogger().addAppender(logger);
		QueueSimulationFactory.createMobsimStatic(f.scenario, events).run();
		Logger.getRootLogger().removeAppender(logger);

		return logger;
	}

	public void testStartAndEndTime() {
		ScenarioImpl scenario = (ScenarioImpl) ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Config config = scenario.getConfig();

		// build simple network with 1 link
		NetworkImpl network = scenario.getNetwork();
		Node node1 = network.getFactory().createNode(scenario.createId("1"), scenario.createCoord(0.0, 0.0));
		Node node2 = network.getFactory().createNode(scenario.createId("2"), scenario.createCoord(1000.0, 0.0));
		network.addNode(node1);
		network.addNode(node2);
		Link link = network.getFactory().createLink(scenario.createId("1"), node1, node2);
		link.setFreespeed(10.0);
		link.setCapacity(2000.0);
		network.addLink(link);

		// build simple population with 1 person with 1 plan with 1 leg
		Population population = scenario.getPopulation();
		PopulationFactory pb = population.getFactory();
		Person person = pb.createPerson(scenario.createId("1"));
		Plan plan = pb.createPlan();
		Activity act1 = pb.createActivityFromLinkId("h", link.getId());
		act1.setEndTime(7.0*3600);
		Leg leg = pb.createLeg(TransportMode.walk);
		GenericRoute route = new GenericRouteImpl(link.getId(), link.getId());
		leg.setRoute(route);
		leg.setTravelTime(5.0*3600);
		Activity act2 = pb.createActivityFromLinkId("w", link.getId());
		plan.addActivity(act1);
		plan.addLeg(leg);
		plan.addActivity(act2);
		person.addPlan(plan);
		population.addPerson(person);

		EventsManager events = EventsUtils.createEventsManager();
		FirstLastEventCollector collector = new FirstLastEventCollector();
		events.addHandler(collector);

		// first test without special settings
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(scenario, events);
		sim.run();
		assertEquals(act1.getEndTime(), collector.firstEvent.getTime(), MatsimTestCase.EPSILON);
		assertEquals(act1.getEndTime() + leg.getTravelTime(), collector.lastEvent.getTime(), MatsimTestCase.EPSILON);
		collector.reset(0);

		// second test with special start/end times
		config.simulation().setStartTime(8.0*3600);
		config.simulation().setEndTime(11.0*3600);
		sim = QueueSimulationFactory.createMobsimStatic(scenario, events);
		sim.run();
		assertEquals(8.0*3600, collector.firstEvent.getTime(), MatsimTestCase.EPSILON);
		assertEquals(11.0*3600, collector.lastEvent.getTime(), MatsimTestCase.EPSILON);
	}

	/**
	 * Tests that cleanupSim() works correctly without generating NullPointerExceptions,
	 * even if there are still agents somewhere in the simulation.
	 *
	 * @author mrieser
	 */
	public void testCleanupSim_EarlyEnd() {
		ScenarioImpl scenario = (ScenarioImpl) ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Config config = scenario.getConfig();

		double simEndTime = 8.0*3600;

		// build simple network with 2 links
		NetworkImpl network = scenario.getNetwork();
		Node node1 = network.getFactory().createNode(scenario.createId("1"), scenario.createCoord(0.0, 0.0));
		Node node2 = network.getFactory().createNode(scenario.createId("2"), scenario.createCoord(1000.0, 0.0));
		Node node3 = network.getFactory().createNode(scenario.createId("3"), scenario.createCoord(2000.0, 0.0));
		network.addNode(node1);
		network.addNode(node2);
		network.addNode(node3);
		Link link1 = network.getFactory().createLink(scenario.createId("1"), node1, node2);
		link1.setFreespeed(10.0); // freespeed-traveltime = 100s
		link1.setCapacity(2000.0);
		network.addLink(link1);
		Link link2 = network.getFactory().createLink(scenario.createId("2"), node2, node3);
		link2.setFreespeed(10.0); // freespeed-traveltime = 100s
		link2.setCapacity(2000.0);
		network.addLink(link2);

		// build simple population with 3 persons with 1 plan with 1 leg
		Population population = scenario.getPopulation();
		PopulationFactory pb = population.getFactory();
		// person 1 : on the road when simulation ends
		Person person1 = pb.createPerson(scenario.createId("1"));
		Plan plan1 = pb.createPlan();
		Activity act1_1 = pb.createActivityFromLinkId("h", link1.getId());
		act1_1.setEndTime(simEndTime - 20);
		Leg leg1 = pb.createLeg(TransportMode.car);
		NetworkRoute route1 = new LinkNetworkRouteImpl(link1.getId(), link2.getId());
		leg1.setRoute(route1);
		leg1.setTravelTime(5.0*3600);
		Activity act1_2 = pb.createActivityFromLinkId("w", link2.getId());
		plan1.addActivity(act1_1);
		plan1.addLeg(leg1);
		plan1.addActivity(act1_2);
		person1.addPlan(plan1);
		population.addPerson(person1);
		// person 2 : on teleportation when simulation ends
		Person person2 = pb.createPerson(scenario.createId("2"));
		Plan plan2 = pb.createPlan();
		Activity act2_1 = pb.createActivityFromLinkId("h", link1.getId());
		act2_1.setEndTime(simEndTime - 1000);
		Leg leg2 = pb.createLeg(TransportMode.walk);
		GenericRoute route2 = new GenericRouteImpl(link1.getId(), link2.getId());
		leg2.setRoute(route2);
		leg2.setTravelTime(2000);
		Activity act2_2 = pb.createActivityFromLinkId("w", link2.getId());
		plan2.addActivity(act2_1);
		plan2.addLeg(leg2);
		plan2.addActivity(act2_2);
		person2.addPlan(plan2);
		population.addPerson(person2);
		// person 3 : still at home when simulation ends
		Person person3 = pb.createPerson(scenario.createId("3"));
		Plan plan3 = pb.createPlan();
		Activity act3_1 = pb.createActivityFromLinkId("h", link1.getId());
		act3_1.setEndTime(simEndTime + 1000);
		Leg leg3 = pb.createLeg(TransportMode.walk);
		GenericRoute route3 = new GenericRouteImpl(link1.getId(), link2.getId());
		leg3.setRoute(route3);
		leg3.setTravelTime(1000);
		Activity act3_2 = pb.createActivityFromLinkId("w", link2.getId());
		plan3.addActivity(act3_1);
		plan3.addLeg(leg3);
		plan3.addActivity(act3_2);
		person3.addPlan(plan3);
		population.addPerson(person3);

		EventsManager events = EventsUtils.createEventsManager();
		FirstLastEventCollector collector = new FirstLastEventCollector();
		events.addHandler(collector);

		// run the simulation
		QueueSimulation sim = QueueSimulationFactory.createMobsimStatic(scenario, events);
		config.simulation().setEndTime(simEndTime);
		sim.run();
		assertEquals(simEndTime, collector.lastEvent.getTime(), MatsimTestCase.EPSILON);
		// besides this, the important thing is that no (Runtime)Exception is thrown during this test
	}

	/**
	 * A simple events handler that counts the number of enter link events on one specific link.
	 * Used by some tests in the class.
	 *
	 * @author mrieser
	 */
	private final static class EnterLinkEventCounter implements LinkEnterEventHandler {
		private final String linkId;
		private int counter = 0;
		public EnterLinkEventCounter(final String linkId) {
			this.linkId = linkId;
		}

		@Override
		public void handleEvent(final LinkEnterEvent event) {
			if (event.getLinkId().toString().equals(this.linkId)) this.counter++;
		}

		@Override
		public void reset(final int iteration) {
			this.counter = 0;
		}

		public int getCounter() {
			return this.counter;
		}
	}

	/*package*/ final static class FirstLastEventCollector implements BasicEventHandler {
		public Event firstEvent = null;
		public Event lastEvent = null;

		@Override
		public void handleEvent(final Event event) {
			if (firstEvent == null) {
				firstEvent = event;
			}
			lastEvent = event;
		}

		@Override
		public void reset(final int iteration) {
			firstEvent = null;
			lastEvent = null;
		}
	}

	/**
	 * Initializes some commonly used data in the tests.
	 *
	 * @author mrieser
	 */
	private static final class Fixture {
		final ScenarioImpl scenario;
		final Config config;
		final NetworkImpl network;
		final Node node1;
		final Node node2;
		final Node node3;
		final Node node4;
		final Link link1;
		final Link link2;
		final Link link3;
		final Population plans;
		final ArrayList<Id> linkIdsNone;
		final ArrayList<Id> linkIds2;

		public Fixture() {
			this.scenario = (ScenarioImpl) ScenarioUtils.createScenario(ConfigUtils.createConfig());
			this.config = scenario.getConfig();
			this.config.addSimulationConfigGroup(new SimulationConfigGroup()) ;
			this.config.simulation().setFlowCapFactor(1.0);
			this.config.simulation().setStorageCapFactor(1.0);
			this.config.simulation().setStuckTime(1.0) ;

			/* build network */
			this.network = scenario.getNetwork();
			this.node1 = this.network.getFactory().createNode(scenario.createId("1"), scenario.createCoord(0, 0));
			this.node2 = this.network.getFactory().createNode(scenario.createId("2"), scenario.createCoord(100, 0));
			this.node3 = this.network.getFactory().createNode(scenario.createId("3"), scenario.createCoord(1100, 0));
			this.node4 = this.network.getFactory().createNode(scenario.createId("4"), scenario.createCoord(1200, 0));
			this.network.addNode(this.node1);
			this.network.addNode(this.node2);
			this.network.addNode(this.node3);
			this.network.addNode(this.node4);
			this.link1 = this.network.getFactory().createLink(scenario.createId("1"), this.node1, this.node2);
			this.link1.setLength(100);
			this.link1.setFreespeed(100);
			this.link1.setCapacity(60000);
			this.link1.setNumberOfLanes(9);
			this.link2 = this.network.getFactory().createLink(scenario.createId("2"), this.node2, this.node3);
			this.link2.setLength(1000);
			this.link2.setFreespeed(100);
			this.link2.setCapacity(6000);
			this.link2.setNumberOfLanes(2);
			this.link3 = this.network.getFactory().createLink(scenario.createId("3"), this.node3, this.node4);
			this.link3.setLength(100);
			this.link3.setFreespeed(100);
			this.link3.setCapacity(60000);
			this.link3.setNumberOfLanes(9);
			this.network.addLink(this.link1);
			this.network.addLink(this.link2);
			this.network.addLink(this.link3);

			/* build plans */
			this.plans = scenario.getPopulation();

			this.linkIdsNone = new ArrayList<Id>(0);

			this.linkIds2 = new ArrayList<Id>(1);
			this.linkIds2.add(this.link2.getId());
		}

	}
}
