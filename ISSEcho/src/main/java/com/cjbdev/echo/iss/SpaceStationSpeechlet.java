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
* @version 1.1
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

private static final String STATE_UNKNOWN = "STATE_UNKNOWN";
private static final String STATE_LIST = "STATE_LIST";

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
    	return handleStateListIntentRequest();
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
private SpeechletResponse handleStateListIntentRequest() {

    return handleStateList(STATE_LIST);
}


/**
 * Creates a {@code SpeechletResponse} for the CityListIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCityListIntentRequest(final Intent intent, final Session session) {

	KeyValuePair statePair = null;
	
	StringBuilder cityStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();
	
	try {

	    Slot stateSlot = intent.getSlot(SLOT_STATE);
	    
	    String stateObject = null;

	    if (stateSlot == null || stateSlot.getValue() == null) {
	    
	    	return handleStateList(STATE_UNKNOWN);
	    } else {
	        // lookup the city. Sample skill uses well known mapping of a few known cities to
	        // station id.
	        stateObject = stateSlot.getValue().trim();
	    }		
		
	    
	    for (KeyValuePair item : STATE_LOOKUP) {
	    	if (item.getKey().toLowerCase().equals(stateObject.toLowerCase())) {
	    		statePair = item;
	    	}
	    }
	    
	    if ((statePair == null) || (statePair.getValue() == null) ) {
	    
	    	return handleStateList(STATE_UNKNOWN);
	    }

		cityStrBldr.append("<speak><p>Locations in " + statePair.getKey() + " that have sighting information are:</p>");
		cardStrBldr.append("Locations in " + statePair.getKey() + " that have sighting information are:\n");	    
	    
		InputStream in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/customSlotTypes/" + statePair.getValue());
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		String sCurrentLine = "";
		while ((sCurrentLine = reader.readLine()) != null) {
			String cityArray[] = sCurrentLine.split(",");
			String city = cityArray[0];
			cityStrBldr.append("<s>" + city + "</s>");
			cardStrBldr.append(city + "\n");
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

	cityStrBldr.append("</speak>");
    String speechText = cityStrBldr.toString();
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("ISS - " + statePair.getKey() + " Location List");
    card.setContent(cardStrBldr.toString());

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(speechText);

    return SpeechletResponse.newTellResponse(smlspeech, card);
}


private SpeechletResponse handleStateList(String option) {
	
	StringBuilder stateStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();
	
	if (option.equals(STATE_UNKNOWN)) {
		stateStrBldr.append("<speak>");
		stateStrBldr.append("<p>The state or region you specified is unknown.</p>");
		stateStrBldr.append("<p>States or regions that have sighting location information are:</p>");
		cardStrBldr.append("The state or region you provided is unknown.\n");
		cardStrBldr.append("States or regions that have sighting location information are:\n");
	}
	else {
		stateStrBldr.append("<speak><p>States or regions that have sighting location information are:</p>");
		cardStrBldr.append("States or regions that have sighting location information are:\n");		
	}
		
	for(KeyValuePair item : STATE_LOOKUP) {
			
		String key = item.getKey();
		    
		stateStrBldr.append("<s>" + key + "</s>");
		cardStrBldr.append(key + "\n");
	}

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

    return SpeechletResponse.newTellResponse(smlspeech, card);
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
            throw new Exception("stateSlot is null!");
	    } else {
	    	// lookup the city. Sample skill uses well known mapping of a few known cities to
	    	// station id.
	    	stateObject = stateSlot.getValue().trim();
	    }		
	    
	    if (citySlot == null || citySlot.getValue() == null) {
	            throw new Exception("citySlot is null!");
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
		
		if (statePair.getKey().equals("National Parks")) {
			issStrBldr.append("The International Space Station will next be visible from ");
			issStrBldr.append(cityObject + " on: ");			
		} 
		else {
			issStrBldr.append("The International Space Station will next be visible from ");
			issStrBldr.append(cityObject);
			issStrBldr.append(", " + stateObject + " on: ");			
		}
		
	    for (KeyValuePair item : cityList) {
	    	if (item.getKey().toLowerCase().equals(cityObject.toLowerCase())) {
	    		cityPair = item;
	    	}
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

	welStrBldr.append("Welcome to the International Space Station Sighter.\n");
	welStrBldr.append("This skill provides sighting information for the International Space Station from certain locations in the United States.\n");
	welStrBldr.append("The International Space Station is the third brightest object in the sky and easy to spot if you know when and where to look up.\n");
	welStrBldr.append("The station is visible to the naked eye and looks like a fast-moving plane.\n");
	
	
    String speechText = welStrBldr.toString();

    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("ISS - Welcome");
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    // Create reprompt
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(speech);

    return SpeechletResponse.newAskResponse(speech, reprompt, card);
}

/**
 * Creates and returns a {@code SpeechletResponse} with a welcome message for help.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleHelpRequest() {

	StringBuilder helpStrBldr = new StringBuilder();

	helpStrBldr.append("This skill provides sighting information for specific locations.\n");
	helpStrBldr.append("The space station is visible for at least a 50 mile (80 km) radius around each location.\n\n");
	helpStrBldr.append("If your city or town isn't available then pick the closest location to you.\n");
	helpStrBldr.append("You need both a state or region and a city or location.\n");
	helpStrBldr.append("For a list of states or regions say: Alexa ask the space station to list states.\n");
	helpStrBldr.append("For a list of locations in a state or region say: Alexa ask the space station to list locations in Maryland.\n");
	helpStrBldr.append("For sighting information say: Alexa ask the space station when it is visible for Gaithersburg Maryland.\n");	
	
    String speechText = helpStrBldr.toString();
    
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("ISS - Help");
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    // Create reprompt
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(speech);

    return SpeechletResponse.newAskResponse(speech, reprompt, card);
}


//@Override
public void onSessionEnded(final SessionEndedRequest request, final Session session)
        throws SpeechletException {
    log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
            session.getSessionId());
}

}