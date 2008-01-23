/* *********************************************************************** *
 * project: org.matsim.*
 * PtControler.java
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

package playground.marcel;

import org.apache.log4j.Logger;
import org.matsim.controler.Controler;
import org.matsim.gbl.Gbl;
import org.matsim.network.NetworkWriter;

import playground.marcel.ptnetwork.PtNetworkLayer;

public class PtControler extends Controler {

	private static final Logger log = Logger.getLogger(PtControler.class);

	PtNetworkLayer ptNetwork = null;

	public PtControler(final String[] args) {
		super(args);
	}

	@Override
	protected void loadData() {
		super.loadData();

		// load Pt Network
		log.info("  reading pt network... ");
		PtNetworkLayer ptNetwork = new PtNetworkLayer();
		ptNetwork.buildfromBERTA(Gbl.getConfig().getParam("network", "inputBvgDataDir"),
				Gbl.getConfig().getParam("network", "inputBvgDataCoords"), true, null);
		log.info("  done");

		String filename = Gbl.getConfig().findParam("network", "outputPtNetworkFile");
		log.info("  writing network xml file to " + filename + "... ");
		if (filename != null) {
			NetworkWriter network_writer = new NetworkWriter(ptNetwork, filename);
			network_writer.write();
		}
		log.info("  done");
	}

	// main-routine, where it all starts...
	public static void main(final String[] args) {
		final PtControler controler = new PtControler(args);
		controler.run();
	}
}
