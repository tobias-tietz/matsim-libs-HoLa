package playground.vsp.bvwp;

import junit.framework.Assert;

import org.matsim.core.basic.v01.IdImpl;

import playground.vsp.bvwp.MultiDimensionalArray.Attribute;
import playground.vsp.bvwp.MultiDimensionalArray.DemandSegment;
import playground.vsp.bvwp.MultiDimensionalArray.Mode;
import static playground.vsp.bvwp.Key.*;


/**
 * @author jbischoff
 *
 

mit
Wolfsburg            Röbel                 310301 1305603    349.53    152.22 =2.537

ohne
Wolfsburg            Röbel                 310301 1305603    320.77    165.15 =2.7525

P2030_2010_verbleibend_ME2.csv
  310301; 1305603;           0;           0;           0;          81;        4209;
P2030_2010_neuentstanden_ME2.csv
  310301; 1305603;           0;           0;           0;           8;         545;
P2030_2010_entfallend_ME2.csv
-
P2030_2010_BMVBS_ME2_131008.csv:
310301;1305603
beruf,ausb,einkauf,gesch,url,priv
Bahn
0;0;0;0;0;2;
PKW
0;0;0;118;81;4209;
Luft
0;0;0;0;0;0;
Bus
0;0;0;0;0;1;
Rad
0;0;0;0;0;0;
Fuß
0;0;0;0;0;0
P2030_2010_A14_verlagert_ME2.wid
-
P2030_2010_A14_induz_ME2.wid
# Widerstände MIV im Ohne- und Mit-Projekt-Fall
# von Micro2, nach Micro2,
# ... Reiseweite (Ohne-fall, Mit-Fall) [km],
# ... Reisezeit (Ohne-(06,08), Mit-Fall(07,09)) [Min],
# ... Fahrzeugvorhaltungskosten und -Betriebskosten (Ohne-(10,12),Mit-Fall(11,13)) [/Pkw-Fahrt],
# ... Nutzerkosten je Reisezweck (Beruf, Ausbildung, Einkauf, Geschäft, Urlaub, Privat) (Ohne-(14),Mit-Fall(15)) [/Pers-Fahrt]
# 
  310301; 1305603;    188.96;    217.73;    206.53;    193.60;     37.58;     42.39;     19.91;     16.84;     12.16;     23.34;      9.52;     10.95;     22.52;     19.06;    13.76;     26.48;     10.77;     12.39;
 */

class ScenarioZielnetzRoad {

	static private final String WolfsburgRoebel = "Wolfsburg--Roebel";

	static ScenarioForEvalData createNullfall1() {
		// set up the base case:
		ScenarioForEvalData nullfall = new ScenarioForEvalData() ;

		// Wolfsburg--Roebel:
		{
			final Values nullfallForOD = new Values() ;
			nullfall.setValuesForODRelation( new IdImpl(WolfsburgRoebel), nullfallForOD ) ;
			{
				Mode mode = Mode.ROAD ;
				//aus P2030_2010_A14_induz_ME2.wid
				//XX aus P2030_2010_BMVBS_ME2_131008.csv:
				//priceUser aus P2030_2010_A14_induz_ME2.wid
				{
					DemandSegment segm = DemandSegment.PV_SONST ;
					nullfallForOD.put(makeKey(mode, segm, Attribute.XX), 4209. ) ; // Menge Strasse im Nullfall = verbleibender Verkehr
					nullfallForOD.put(makeKey(mode, segm, Attribute.hrs), 206.53/60. ) ; // Fahrzeit Strasse im Nullfall
					nullfallForOD.put(makeKey(mode, segm, Attribute.priceUser), 10.95) ; // Nutzerkosten Strasse im Nullfall
					nullfallForOD.put( makeKey( mode, segm, Attribute.costOfProduction ), 20. ) ; // ???  Produktionskosten Strasse im Nullfall
				}
			}
			{
				Mode mode = Mode.RAIL ;
				{
					DemandSegment segm = DemandSegment.PV_SONST ;
					nullfallForOD.put(makeKey(mode, segm, Attribute.XX), 15. ) ;   // Menge Bahn im Nullfall.  Wenn nicht bekannt, ggf. die gesamte verlagerte Menge
					nullfallForOD.put(makeKey(mode, segm, Attribute.hrs), 300./60. ) ; // Fahrzeit Bahn
					nullfallForOD.put(makeKey(mode, segm, Attribute.priceUser), 15.) ; // ??? Nutzerkosten Bahn
					nullfallForOD.put( makeKey( mode, segm, Attribute.costOfProduction ), 0. ) ; // Produktionskosten sind Null bei Bahn.
				}
			}
		}
		// end Wolfsburg--Roebel

		// return the base case:
		return nullfall;
	}

	static ScenarioForEvalData createPlanfallStrassenausbau(ScenarioForEvalData nullfall) {
		// The policy case is initialized as a complete copy of the base case:
		ScenarioForEvalData planfall = nullfall.createDeepCopy() ;

		// Wolfsburg--Roebel:
		{
			Values planfallValuesForOD = planfall.getByODRelation(new IdImpl(WolfsburgRoebel)) ;
			Assert.assertNotNull(planfallValuesForOD) ;
			{
				DemandSegment segm = DemandSegment.PV_SONST ;
				planfallValuesForOD.inc( makeKey( Mode.ROAD, segm, Attribute.hrs), -0.1 ) ; // Fahrzeit Strasse im Planfall 
				planfallValuesForOD.put( makeKey( Mode.RAIL, segm, Attribute.XX), 0. ) ; // Menge Bahn im Planfall.  Wenn nicht bekannt, dann jetzt Null. 
				planfallValuesForOD.inc( makeKey( Mode.ROAD, segm, Attribute.XX), 20. ) ; // Menge Straße im Planfall.
				
				// später zu berechnen: Menge Str im Planfall - Menge Str im Nullfall - Menge verlagert = Menge induziert
			}
		}
		// end Wolfsburg--Roebel

		return planfall;
	}

}

