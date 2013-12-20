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

package playground.anhorni.surprice.preprocess.sc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.matsim.analysis.Bins;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.facilities.ActivityFacility;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.facilities.ActivityFacilityImpl;
import org.matsim.core.facilities.FacilitiesReaderMatsimV1;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationFactoryImpl;
import org.matsim.core.population.PopulationWriter;
import org.matsim.core.router.old.TeleportationLegRouter;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import playground.anhorni.surprice.DayConverter;
import playground.anhorni.surprice.Surprice;


public class CreatePlans {	
	private ScenarioImpl scenario = (ScenarioImpl) ScenarioUtils.createScenario(ConfigUtils.createConfig());
	private ObjectAttributes incomes = new ObjectAttributes();
	private ObjectAttributes preferences = new ObjectAttributes();
	private Random rnd = new Random(221177);
		
	private final static Logger log = Logger.getLogger(CreatePlans.class);
		
	public static void main(final String[] args) {		
		if (args.length != 1) {
			log.error("Provide correct number of arguments ...");
			System.exit(-1);
		}		
		CreatePlans creator = new CreatePlans();
		creator.run(args[0]);
	}
	
	public void run(String outPath) {
		int nbrPersons = 500;
		for (int i = 0; i < nbrPersons; i++) {
			PersonImpl person = new PersonImpl(new IdImpl(i));
			this.scenario.getPopulation().addPerson(person);
			person.createAndAddPlan(true);
			Plan plan = person.getSelectedPlan();
			
			int offset = 1; //rnd.nextInt(600);
			ActivityImpl homeAct = ((PlanImpl) plan).createAndAddActivity("home");
			homeAct.setEndTime(6.0 * 3600.0 + offset);
			
			homeAct.setFacilityId(new IdImpl(1));
			homeAct.setCoord(new CoordImpl(-100.0, 0.0));
			homeAct.setLinkId(new IdImpl(1));
			
			((PlanImpl) plan).createAndAddLeg("car");
						
			ActivityImpl act;
			
			if (i < nbrPersons/2.0) act = ((PlanImpl) plan).createAndAddActivity("work");
			else act = ((PlanImpl) plan).createAndAddActivity("leisure");
			
			act.setStartTime(6.0 * 3600.0 + 100.0 + 1.0 * offset);
			
			act.setFacilityId(new IdImpl(2));
			act.setCoord(new CoordImpl(2100.0, 0.0));
			act.setLinkId(new IdImpl(5));
			
			
			if (i % 2 == 0) {
				incomes.putAttribute(person.getId().toString(), "income", new Double(0.2));
				preferences.putAttribute(person.getId().toString(), "dudm", new Double(0.1));
			} else {
				incomes.putAttribute(person.getId().toString(), "income",new Double(0.1));
				preferences.putAttribute(person.getId().toString(), "dudm", new Double(1.0));
			}
			((PersonImpl)person).createDesires("desires");
			((PersonImpl)person).getDesires().putActivityDuration("home", 8.0 * 3600.0);	
			((PersonImpl)person).getDesires().putActivityDuration("work", 8.0 * 3600.0);
			((PersonImpl)person).getDesires().putActivityDuration("leisure", 8.0 * 3600.0);
		}
		this.write(outPath);
	}
	
	private void write(String outPath) {
		ObjectAttributesXmlWriter attributesWriter = new ObjectAttributesXmlWriter(preferences);
		attributesWriter.writeFile(outPath + "/preferences.xml");
		
		ObjectAttributesXmlWriter incomesWriter = new ObjectAttributesXmlWriter(incomes);
		incomesWriter.writeFile(outPath + "/incomes.xml");
		
		new PopulationWriter(
				this.scenario.getPopulation(), scenario.getNetwork()).writeFileV4(outPath + "/mon/plans.xml.gz");
	}
}
