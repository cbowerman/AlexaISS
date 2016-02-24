package com.cjbdev.echo.iss;
/**
Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazon.speech.ui.SsmlOutputSpeech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;


import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

/**
* @author Christopher Bowerman
* @version 1.2
* 
* Creates a Lambda function for handling Alexa Skill requests that:
* <ul>
* <li>Tells when the International Space Station is next visible from given locations in the United States.</li>
* <li>Lists the locations for which sighting information is available for a state/region.</li>
* <li>Lists acceptable state/regions.</li>
* </ul>
* <p>
* <h2>Examples</h2>
* <p>
* User: "Alexa, ask the space station when it is visible from Gaithersburg Maryland" 
* Alexa: "The International Spaces Station will be visible from Gaithersburg, Maryland on: ..."
* <p>
* User: "Alexa, ask the space station to list locations for Maryland" 
* Alexa: "Locations in Maryland that have sighting information are: ..."
* <p>
* User: "Alexa, ask the space station to list states" 
* Alexa: "States or regions that have sighting information are: ..."
* 
*/

public class SpaceStationSpeechlet implements Speechlet {
	
private static final Logger log = LoggerFactory.getLogger(SpaceStationSpeechlet.class);

private static final String SLOT_CITY = "City";
private static final String SLOT_STATE = "State";
private static final String SLOT_LETTER = "FirstLetter";

private static final String STATE_UNKNOWN = "STATE_UNKNOWN";
private static final String STATE_LIST = "STATE_LIST";
private static final String CITY_UNKNOWN = "CITY_UNKNOWN";
private static final String CITY_LIST = "CITY_LIST";

static SpaceStationListLoader ssListLoader = new SpaceStationListLoader();

private static final List<KeyValuePair> STATE_LOOKUP = ssListLoader.loadStateInfo();

//@Override
public void onSessionStarted(final SessionStartedRequest request, final Session session)
        throws SpeechletException {
    log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
            session.getSessionId());

    // any initialization logic goes here
}

//@Override
public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
        throws SpeechletException {
    log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
            session.getSessionId());

    return getWelcomeResponse();
}

//@Override
public SpeechletResponse onIntent(final IntentRequest request, final Session session)
        throws SpeechletException {
    log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
            session.getSessionId());

    Intent intent = request.getIntent();
    String intentName = intent.getName();

    if ("StateListIntent".equals(intentName)) {
    	return handleStateListIntentRequest(intent, session);
    } else if ("CityListIntent".equals(intentName)) {
    	return handleCityListIntentRequest(intent, session);
    } else if ("CityStateIntent".equals(intentName)) {
    	return handleCityStateIntentRequest(intent, session);
    } else if ("AMAZON.HelpIntent".equals(intentName)) {
        return handleHelpRequest();
    } else if ("AMAZON.StopIntent".equals(intentName)) {
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText("Goodbye");
        
        return SpeechletResponse.newTellResponse(outputSpeech);
    } else if ("AMAZON.CancelIntent".equals(intentName)) {
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText("Goodbye");

        return SpeechletResponse.newTellResponse(outputSpeech);
    } else {
        throw new SpeechletException("Invalid Intent");
    }
}


/**
 * Creates a {@code SpeechletResponse} for the StateListIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleStateListIntentRequest(final Intent intent, final Session session) {

    return handleStateList(intent, session, STATE_LIST);
}


/**
 * Creates a {@code SpeechletResponse} for the CityListIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCityListIntentRequest(final Intent intent, final Session session) {
	
	Slot stateSlot = intent.getSlot(SLOT_STATE);
	Slot citySlot = intent.getSlot(SLOT_CITY);
	
	if ((stateSlot == null || stateSlot.getValue() == null) && (citySlot == null || citySlot.getValue() == null)) {
		StringBuilder cityStrBldr = new StringBuilder();
		
		cityStrBldr.append("<speak>");
		cityStrBldr.append("<p>To list cities or locations a state or region is required.</p>");
		cityStrBldr.append("<p>For a list of locations in a certain state or region say list locations in Maryland or another state.</p>");
		cityStrBldr.append("<p>Shorten the list by saying list locations in Maryland beginning with A or another letter.</p>");
		cityStrBldr.append("</speak>");
	    
	    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
	    smlspeech.setSsml(cityStrBldr.toString());
	    
	    StringBuilder rpStrBldr = new StringBuilder();
		rpStrBldr.append("<speak>");
		rpStrBldr.append("For a lists of states or regions say list states.");
		rpStrBldr.append("</speak>");

	    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
	    rpsmlspeech.setSsml(rpStrBldr.toString());

	    // Create reprompt
	    Reprompt reprompt = new Reprompt();
	    reprompt.setOutputSpeech(rpsmlspeech);	    
	    
	    return SpeechletResponse.newAskResponse(smlspeech, reprompt);
	}

	return handleCityList(intent, session, CITY_LIST);
}


private SpeechletResponse handleStateList(final Intent intent, final Session session, String option) {
	
	StringBuilder stateStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();

	Slot letterSlot = intent.getSlot(SLOT_LETTER);
	boolean shortList = true;
	
	if (letterSlot == null || letterSlot.getValue() == null) {
		shortList = false;
	}
		
	if (option.equals(STATE_UNKNOWN)) {

		StringBuilder rpStrBldr = new StringBuilder();
		
		stateStrBldr.append("<speak>");
		stateStrBldr.append("<p>The state or region you specified is unknown.</p>");
		stateStrBldr.append("<p>You can get the full a list of states or regions by saying list states.</p>");
		stateStrBldr.append("<p>You can shorten the list by saying list states starting with A or any other letter.</p>");
		stateStrBldr.append("</speak>");
		
		rpStrBldr.append("<speak>");
		rpStrBldr.append("For a lists of states or regions say list states.");
		rpStrBldr.append("</speak>");
	    
	    // Create the plain text output.
	    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
	    smlspeech.setSsml(stateStrBldr.toString());

	    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
	    rpsmlspeech.setSsml(rpStrBldr.toString());

	    // Create reprompt
	    Reprompt reprompt = new Reprompt();
	    reprompt.setOutputSpeech(rpsmlspeech);

	    return SpeechletResponse.newAskResponse(smlspeech, reprompt);						
		
	}
	else {
		stateStrBldr.append("<speak><p>States or regions that have sighting location information are:</p>");
		cardStrBldr.append("States or regions that have sighting location information are:\n");		
	}
		
	for(KeyValuePair item : STATE_LOOKUP) {
			
		String key = item.getKey();
		
		if (shortList) {
			
			if (key.toLowerCase().charAt(0) == letterSlot.getValue().toLowerCase().charAt(0)) {
				stateStrBldr.append("<s>" + key + "</s>");
				cardStrBldr.append(key + "\n");				
			}
		}
		else {
			stateStrBldr.append("<s>" + key + "</s>");
			cardStrBldr.append(key + "\n");
		}
	}
	
	stateStrBldr.append("<p>You can get a list locations with sighting information within a state by saying "
			+ "list locations in Maryland or some other state.</p>");
	stateStrBldr.append("<p>Or this list can be narrowed by saying list locations in Maryland starting with B or another letter.</p>");
	cardStrBldr.append("You can get a list locations with sighting information within a state by saying "
			+ "list locations in Maryland or some other state.\n");		
	cardStrBldr.append("Or this list can be narrowed by saying list locations in Maryland beginning with B or another letter.\n");
	
	stateStrBldr.append("</speak>");
    String speechText = stateStrBldr.toString();
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    if (option.equals(STATE_UNKNOWN)) {
    	card.setTitle("ISS - Unknown State");	
    }
    else {
    	card.setTitle("ISS - Sighting Information State/Region List");    	
    }
    
    card.setContent(cardStrBldr.toString());

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(speechText);
  
    StringBuilder rpStrBldr = new StringBuilder();
    rpStrBldr.append("<speak>");
    rpStrBldr.append("<p>You can get a list cities or locations with sighting information within a state by saying "
			+ "list locations in Maryland or some other state.</p>");
	rpStrBldr.append("<p>Or this list can be narrowed by saying list locations in Maryland beginning with B or another letter.</p>");
	rpStrBldr.append("</speak>");
	
    Reprompt reprompt = new Reprompt();
    SsmlOutputSpeech rpSpeech = new  SsmlOutputSpeech();
    rpSpeech.setSsml(rpStrBldr.toString());

    reprompt.setOutputSpeech(rpSpeech);
    
    return SpeechletResponse.newAskResponse(smlspeech, reprompt, card);
}


private SpeechletResponse handleCityList(final Intent intent, final Session session, String option) {
	
	KeyValuePair statePair = null;
	
	StringBuilder cityStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();
	
	try {

	    Slot stateSlot = intent.getSlot(SLOT_STATE);
	
		Slot letterSlot = intent.getSlot(SLOT_LETTER);
		boolean shortList = true;
		
		if (letterSlot == null || letterSlot.getValue() == null) {
			shortList = false;
		}	    
	    
	    String stateObject = null;

	    if (stateSlot == null || stateSlot.getValue() == null) {
	    
	    	return handleStateList(intent, session, STATE_UNKNOWN);
	    } else {
	    	
	        // lookup the state.
	        stateObject = stateSlot.getValue().trim();
	    }		
		
	    
	    for (KeyValuePair item : STATE_LOOKUP) {
	    	if (item.getKey().toLowerCase().equals(stateObject.toLowerCase())) {
	    		statePair = item;
	    	}
	    }
	    
	    if ((statePair == null) || (statePair.getValue() == null) ) {
	    
	    	return handleStateList(intent, session, STATE_UNKNOWN);
	    }
	    
		InputStream in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/customSlotTypes/" + statePair.getValue());
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		if (option.equals(CITY_UNKNOWN)) {
			
			StringBuilder rpStrBldr = new StringBuilder();
			
			cityStrBldr.append("<speak>");
			cityStrBldr.append("<p>The location you specified does not have sighting information available.</p>");
			cityStrBldr.append("<p>You can get locations by saying list locations in " + statePair.getKey() + "</p>");
			cityStrBldr.append("<p>Shorten the list by saying list locations in " + statePair.getKey() + " starting with A or another letter.</p>");
			cityStrBldr.append("</speak>");
			
			rpStrBldr.append("<speak>");
			rpStrBldr.append("<p>For a listing of locations say list locations in " + statePair.getKey() + ".</p>");
			rpStrBldr.append("</speak>");
		    
		    // Create the plain text output.
		    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
		    smlspeech.setSsml(cityStrBldr.toString());

		    
		    // Create reprompt
		    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
		    rpsmlspeech.setSsml(rpStrBldr.toString());
		    Reprompt reprompt = new Reprompt();
		    reprompt.setOutputSpeech(rpsmlspeech);

		    return SpeechletResponse.newAskResponse(smlspeech, reprompt);						
			
		}
		else {
			cityStrBldr.append("<speak><p>Locations in " + statePair.getKey() + " that have sighting information are:</p>");
			cardStrBldr.append("Locations in " + statePair.getKey() + " that have sighting information are:\n");		
		}		
		
		
		String sCurrentLine = "";
		int counter = 0;
		while ((sCurrentLine = reader.readLine()) != null) {
			String cityArray[] = sCurrentLine.split(",");
			String city = cityArray[0];
			
			if (shortList) {
				
				if (city.toLowerCase().charAt(0) == letterSlot.getValue().toLowerCase().charAt(0)) {
					cityStrBldr.append("<s>" + city + "</s>");
					cardStrBldr.append(city + "\n");
					counter++;
				}
			}
			else {
				cityStrBldr.append("<s>" + city + "</s>");
				cardStrBldr.append(city + "\n");
				counter++;
			}					
		}
		
		// Handle if no locations are returned.
		if (counter == 0) {

			StringBuilder noStrBldr = new StringBuilder();
			StringBuilder rpStrBldr = new StringBuilder();
			
			noStrBldr.append("<speak>");
			noStrBldr.append("<p>There does not appear to be any locations matching your criteria.</p>");
			noStrBldr.append("<p>You can get locations by saying list locations in " + statePair.getKey() + "</p>");
			noStrBldr.append("<p>Shorten the list by saying list locations in " + statePair.getKey() + " starting with A or another letter.</p>");
			noStrBldr.append("</speak>");
			
			rpStrBldr.append("<speak>");
			rpStrBldr.append("<p>For a listing of locations say list locations in " + statePair.getKey() + ".</p>");
			rpStrBldr.append("</speak>");
		    
		    // Create the plain text output.
		    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
		    smlspeech.setSsml(cityStrBldr.toString());

		    
		    // Create reprompt
		    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
		    rpsmlspeech.setSsml(rpStrBldr.toString());
		    Reprompt reprompt = new Reprompt();
		    reprompt.setOutputSpeech(rpsmlspeech);

		    return SpeechletResponse.newAskResponse(smlspeech, reprompt);						
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

	cityStrBldr.append("<p>You can get sighting information for a location by saying "
			+ "give me visibility for Gaithersburg Maryland or some other location and state combination.</p>");
	cardStrBldr.append("You can get sighting information for a location by saying "
			+ "give me visibility for Gaithersburg Maryland or some other location and state combination.\n");			
	cityStrBldr.append("</speak>");
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("ISS - " + statePair.getKey() + " Location List");    	
    
    card.setContent(cardStrBldr.toString());

    // Create the ssmloutput text output.
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(cityStrBldr.toString());

    StringBuilder rpStrBldr = new StringBuilder();
	rpStrBldr.append("<speak>");
	rpStrBldr.append("<p>You can get sighting information for a location by saying "
			+ "give me visibility for Gaithersburg Maryland or some other location and state combination.</p>");
	rpStrBldr.append("</speak>");
    
    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
    rpsmlspeech.setSsml(rpStrBldr.toString());
    
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(rpsmlspeech);
    
    return SpeechletResponse.newAskResponse(smlspeech, reprompt, card);
}

/**
 * Creates a {@code SpeechletResponse} for the OneshotCityIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCityStateIntentRequest(final Intent intent, final Session session) {
	
    String cityObject = null;
    String stateObject = null;
	StringBuilder issStrBldr = new StringBuilder();
	
	try {

	    Slot citySlot = intent.getSlot(SLOT_CITY);
	    Slot stateSlot = intent.getSlot(SLOT_STATE);
	    KeyValuePair statePair = null;
	    	    
	    if (stateSlot == null || stateSlot.getValue() == null) {
	    	
	    	return handleStateList(intent, session, STATE_UNKNOWN);
	    } else {
	    	// lookup the city. Sample skill uses well known mapping of a few known cities to
	    	// station id.
	    	stateObject = stateSlot.getValue().trim();
	    }		
	    
	    if (citySlot == null || citySlot.getValue() == null) {
	    		
	    	return handleCityList(intent, session, CITY_UNKNOWN);
	    } else {
	        // lookup the city. Sample skill uses well known mapping of a few known cities to
	        // station id.
	        cityObject = citySlot.getValue().trim();
	    }		
	    
	    for (KeyValuePair item : STATE_LOOKUP) {

	    	if (item.getKey().toLowerCase().equals(stateObject.toLowerCase())) {
	    		
	    		statePair = item;
	    	}
	    }
	    
	    if ((statePair == null) || (statePair.getValue() == null) ) {
		    
	    	return handleStateList(intent, session, STATE_UNKNOWN);
	    }
	    
		if (statePair.getKey().equals("National Parks")) {
			issStrBldr.append("The International Space Station will next be visible from ");
			issStrBldr.append(cityObject + " on: ");			
		} 
		else {
			issStrBldr.append("The International Space Station will next be visible from ");
			issStrBldr.append(cityObject);
			issStrBldr.append(", " + stateObject + " on: ");			
		}	    
	    
		InputStream in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/customSlotTypes/" + statePair.getValue());
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		
		List<KeyValuePair> cityList = new ArrayList<KeyValuePair>();
				
		String sCurrentLine = "";
		while ((sCurrentLine = reader.readLine()) != null) {
			String cityArray[] = sCurrentLine.split(",");
			KeyValuePair cityItem = new KeyValuePair(cityArray[0], cityArray[1]);
			cityList.add(cityItem);
		}
		
		KeyValuePair cityPair = null;
		
	    for (KeyValuePair item : cityList) {
	    	if (item.getKey().toLowerCase().equals(cityObject.toLowerCase())) {
	    		cityPair = item;
	    	}
	    }
	    
	    if (cityPair == null) {
	    	
	    	return handleCityList(intent, session, CITY_UNKNOWN);
	    }
		
		URL url = new URL("http://spotthestation.nasa.gov/sightings/xml_files/" + cityPair.getValue() + ".xml");
		HttpURLConnection httpcon = (HttpURLConnection)url.openConnection();

		// Reading the feed
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = input.build(new XmlReader(httpcon));
		List<SyndEntry> entries = feed.getEntries();
		Iterator<SyndEntry> itEntries = entries.iterator();
		
		boolean first = true;
		String firstDesc = "";

		while (itEntries.hasNext()) {
			SyndEntry entry = itEntries.next();
			SyndContent desc = entry.getDescription();
			String descStr = desc.getValue();
			String descStrMod = descStr.replaceAll("<br/>", "");
			
			String durationSplitArray[] = descStrMod.split("Duration");
			String dateTimeStr = durationSplitArray[0];
			String dateTimeSplitArray[] = dateTimeStr.split("Time:");
			String dateSplitStr = dateTimeSplitArray[0];
			String dateArray[] = dateSplitStr.split("Date:");
			String sightDate = dateArray[1].trim() + " " + dateTimeSplitArray[1].trim();
						
			SimpleDateFormat formatter = new SimpleDateFormat("EEEE MMM dd, yyyy hh:mm a");

			Calendar cal = Calendar.getInstance();
			Calendar future = Calendar.getInstance();
			future.setTime(formatter.parse(sightDate));
		    			
			if ((future.compareTo(cal)>0) && first) {
				firstDesc = descStrMod;
				first = false;
			}
			
		}	
		issStrBldr.append(firstDesc);	
	}
	catch (MalformedURLException muex) {
		System.out.println("MalformedURLException" + muex.getMessage());
	}
	catch (IOException ioex) {
		System.out.println("IOException" + ioex.getMessage());
	}
	catch (FeedException fex) {
		System.out.println("FeedException" + fex.getMessage());
	}
	catch (Exception ex) {
		System.out.println("Exeption" + ex.getMessage());
	}
	
    String speechText = issStrBldr.toString();

    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    
    card.setTitle("ISS - Sighting Information for " + cityObject + ", " + stateObject);
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);
    
    return SpeechletResponse.newTellResponse(speech, card);
}


/**
 * Creates and returns a {@code SpeechletResponse} with a welcome message.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse getWelcomeResponse() {

	StringBuilder welStrBldr = new StringBuilder();
	StringBuilder rpStrBldr = new StringBuilder();
	
	welStrBldr.append("<speak>");
	welStrBldr.append("<p>Welcome to the International Space Station Sighter.</p>");
	welStrBldr.append("<p>I provide sighting information for the International Space Station from certain locations in the United States.</p>");
	welStrBldr.append("<p>The space station is visible to the naked eye and is the third brightest object in the sky.</p>");
	welStrBldr.append("<p>It looks like a fast-moving plane and is easy to spot if you know when and where to look up.</p>");
	welStrBldr.append("<p>You can get sighting information by saying give me visibilty from Gaithersburg Maryland or another location and state.</p>");
	welStrBldr.append("<p>I can list the locations in a state by saying list locations in Maryland or another state.</p>");
	welStrBldr.append("<p>Shorten the list by saying list locations in Maryland starting with A or another letter.</p>");
	welStrBldr.append("<p>What would you like to do?</p>");
    welStrBldr.append("</speak>");
	
	rpStrBldr.append("<speak>");	
	rpStrBldr.append("For a listing of locations in a state say list locations in Maryland or another state.");
	rpStrBldr.append("</speak>");
	
    // Create the ssmloutput text output.
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(welStrBldr.toString());

    
    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
    rpsmlspeech.setSsml(rpStrBldr.toString());
    
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(rpsmlspeech);
    
    return SpeechletResponse.newAskResponse(smlspeech, reprompt);
}

/**
 * Creates and returns a {@code SpeechletResponse} with a welcome message for help.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleHelpRequest() {

	StringBuilder helpStrBldr = new StringBuilder();
	StringBuilder rpStrBldr = new StringBuilder();

	helpStrBldr.append("<speak>");
	helpStrBldr.append("<p>I provide sighting information for the International Space Station from specific locations in the United States.</p>");
	helpStrBldr.append("<p>The space station is visible for at least a 50 mile (80 km) radius around available location.</p>");
	helpStrBldr.append("<p>If your specific location is not available then pick the closest location to you.</p>");
	helpStrBldr.append("<p>You can get a list of locations in a state by saying list locations in Maryland or another state.</p>");
	helpStrBldr.append("<p>Or you can get sighting information by saying give me visability from Gaithersburg Maryland or another location and state.</p>");
	helpStrBldr.append("<p>Shorten the list by saying list locations in Maryland starting with A or another letter.</p>");
	helpStrBldr.append("<p>What would you like to do?</p>");
	helpStrBldr.append("</speak>");
	
	rpStrBldr.append("<speak>");
	rpStrBldr.append("<p>For a listing of locations in a state say list locations in Maryland or another state.</p>");
	rpStrBldr.append("</speak>");
	
    // Create the ssmloutput text output.
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(helpStrBldr.toString());

    
    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
    rpsmlspeech.setSsml(rpStrBldr.toString());
    
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(rpsmlspeech);
    
    return SpeechletResponse.newAskResponse(smlspeech, reprompt);
}


//@Override
public void onSessionEnded(final SessionEndedRequest request, final Session session)
        throws SpeechletException {
    log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
            session.getSessionId());
}

}