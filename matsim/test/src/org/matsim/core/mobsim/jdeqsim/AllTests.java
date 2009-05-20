/* *********************************************************************** *
 * project: org.matsim.*
 * AllTests.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.core.mobsim.jdeqsim;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("Tests for " + AllTests.class.getPackage().getName());
		//$JUnit-BEGIN$
		
		suite.addTestSuite(TestDESStarter_EquilPopulationPlans1Modified1.class);
		suite.addTestSuite(TestDESStarter_equilPlans100.class);
		suite.addTestSuite(TestDESStarter_Berlin.class);
		
		suite.addTestSuite(ConfigParameterTest.class);
		
		suite.addTestSuite(TestEventLog.class);
		suite.addTestSuite(TestMessageFactory.class);
		suite.addTestSuite(TestMessageQueue.class);
		suite.addTestSuite(TestScheduler.class);
		
		suite.addTest(org.matsim.core.mobsim.jdeqsim.util.AllTests.suite());
		//$JUnit-END$
		return suite;
	}

}
