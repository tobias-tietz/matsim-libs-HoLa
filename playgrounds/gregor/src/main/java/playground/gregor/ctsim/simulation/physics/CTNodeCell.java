package playground.gregor.ctsim.simulation.physics;
/* *********************************************************************** *
 * project: org.matsim.*
 *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;
import org.matsim.api.core.v01.network.Link;

/**
 * Created by laemmel on 12/10/15.
 */
public class CTNodeCell extends CTCell {

	public CTNodeCell(double x, double y, CTNetwork net, CTNetworkEntity parent) {
		super(x, y, net, parent);
	}

	@Override
	double getFHHi(CTPed ped, CTCellFace face) {
		CTNetworkEntity nbp = face.nb.getParent();
		if (nbp instanceof CTLink) {
			Link usLink = ((CTLink) nbp).getUsLink();
			Link dsLink = ((CTLink) nbp).getDsLink();
			if (ped.getNextLinkId() == usLink.getId()) {
				return 1;
			}
			else {
				if (ped.getNextLinkId() == dsLink.getId()) {
					return 1;
				}
			}
		}
		return 0;
	}

	@Override
	public void jumpOffPed(CTPed ctPed) {
		ctPed.notifyMoveOverNode();
		super.jumpOffPed(ctPed);
	}

	public void init() {
		GeometryFactory geofac = new GeometryFactory();
		Coordinate[] coords = new Coordinate[getFaces().size() * 2];
		int idx = 0;
		for (CTCellFace face : getFaces()) {
			coords[idx++] = new Coordinate(face.x0, face.y0);
			coords[idx++] = new Coordinate(face.x1, face.y1);
		}
		MultiPoint mp = geofac.createMultiPoint(coords);
		setArea(mp.convexHull().getArea());
	}
}