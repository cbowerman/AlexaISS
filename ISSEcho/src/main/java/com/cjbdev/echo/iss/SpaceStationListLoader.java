package com.cjbdev.echo.iss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

class SpaceStationListLoader {

	
	/*
	 * Load up State list
	 */
	List<KeyValuePair> loadStateInfo() {
		List<KeyValuePair> state_list = new ArrayList<KeyValuePair>();
		
		try {

			InputStream in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/customSlotTypes/LIST_OF_STATES");
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));

			String sCurrentLine = "";
			while ((sCurrentLine = reader.readLine()) != null) {

				String stateArray[] = sCurrentLine.split(",");
				KeyValuePair pair = new KeyValuePair(stateArray[0], stateArray[1]);
				state_list.add(pair);
			}
			
			in.close();
		}
		catch (MalformedURLException muex) {
			System.out.println("MalformedURLException" + muex.getMessage());
		}
		catch (IOException ioex) {
			System.out.println("IOException" + ioex.getMessage());
		}
		catch (Exception ex) {
			System.out.println("Exeption" + ex.getMessage());
		}	
		
		return state_list;
	}	
}
