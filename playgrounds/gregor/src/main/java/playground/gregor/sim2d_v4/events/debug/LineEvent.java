/* *********************************************************************** *
 * project: org.matsim.*
 * LineEvent.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package playground.gregor.sim2d_v4.events.debug;

import org.matsim.core.api.experimental.events.Event;

import playground.gregor.sim2d_v4.simulation.physics.PhysicalSim2DSection.Segment;

public class LineEvent extends Event {

	private static final String TYPE = "LINE_EVENT";
	
	private final boolean isStatic;
	private final Segment s;

	private final int r,g,b,a,minScale;
	
	public LineEvent(double time,Segment s, boolean isStatic) {
		this(time, s, isStatic, 0, 0, 0, 255, 0);
	}
	
	public LineEvent(double time, Segment s, boolean isStatic, int r, int g, int b, int a, int minScale) {
		super(time);
		this.s = s;
		this.isStatic = isStatic;
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
		this.minScale = minScale;
		
	}

	@Override
	public String getEventType() {
		return TYPE;
	}
	
	public Segment getSegment() {
		return this.s;
	}
	
	public boolean isStatic() {
		return this.isStatic;
	}

	public int getMinScale() {
		return minScale;
	}

	public int getA() {
		return a;
	}

	public int getB() {
		return b;
	}

	public int getG() {
		return g;
	}

	public int getR() {
		return r;
	}

}
