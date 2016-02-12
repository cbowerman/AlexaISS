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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.apache.commons.lang3.text.WordUtils;

/**
* @author Christopher Bowerman
* @version 1.1
* 
* Creates a Lambda function for handling Alexa Skill requests that:
* <ul>
* <li>Tells when the International Space Station is next visible from given locations in Maryland.</li>
* <li>Lists the locations for which visibility information is available.</li>
* <li>Tells the current crew aboard the international space station.</li>
* </ul>
* <p>
* <h2>Examples</h2>
* <p>
* User: "Alexa, ask the space station when it is next visible from Gaithersburg" 
* Alexa: "The International Spaces Station will be visible from Gaithersburg, Maryland on: ..."
* <p>
* User: "Alexa, ask the space to list cities" 
* Alexa: "Maryland locations I can give visibility information for are: ..."
* <p>
* User: "Alexa, ask the space station who is the current crew" 
* Alexa: "The current crew of the International Spaces Station is...."
* 
*/

public class SpaceStationSpeechlet implements Speechlet {
	
private static final Logger log = LoggerFactory.getLogger(SpaceStationSpeechlet.class);

private static final String SLOT_CITY = "City";
private static final String SLOT_STATE = "State";

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

    if ("CrewIntent".equals(intentName)) {
    	return handleCrewIntentRequest();    	
    } else if ("VisibilityIntent".equals(intentName)) {
    	return handleVisibilityIntentRequest();
    } else if ("OneshotCityIntent".equals(intentName)) {
    	return handleOneshotCityIntentRequest(intent, session);
    } else if ("CityStateIntent".equals(intentName)) {
    	return handleCityStateIntentRequest(intent, session);
    } else if ("CityListIntent".equals(intentName)) {
    	return handleCityListIntentRequest(intent, session);
    } else if ("StateListIntent".equals(intentName)) {
    	return handleStateListIntentRequest();
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
 * Creates a {@code SpeechletResponse} for the CrewIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCrewIntentRequest() {
	
	String link = "";
	StringBuilder crewStrBldr = new StringBuilder();
	crewStrBldr.append("The current crew of the International Space Station is:\n");
	try {
		URL url = new URL("http://cjbcloudiss.s3-website-us-east-1.amazonaws.com/iss-crew-info.xml");
		HttpURLConnection httpcon = (HttpURLConnection) url.openConnection();
		// Reading the feed
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = input.build(new XmlReader(httpcon));
		List<SyndEntry> entries = feed.getEntries();

		SyndEntry entry = entries.get(0);
		
		link = entry.getLink();
		crewStrBldr.append(entry.getDescription().getValue());
		
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
			
    String speechText = crewStrBldr.toString();

    StringBuffer cardStrBldr = new StringBuffer();
    cardStrBldr.append(speechText);
    cardStrBldr.append("\n");
    cardStrBldr.append("For more information:\n");
    cardStrBldr.append(link);
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("ISS - Current Crew");
    card.setContent(cardStrBldr.toString());

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    return SpeechletResponse.newTellResponse(speech, card);
}

/**
 * Creates a {@code SpeechletResponse} for the VisibilityIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleVisibilityIntentRequest() {
	
	StringBuilder issStrBldr = new StringBuilder();
	issStrBldr.append("The international space station will next be visible from Gaithersburg, Maryland on: ");
 
	try {

		URL url = new URL("http://spotthestation.nasa.gov/sightings/xml_files/United_States_Maryland_Gaithersburg.xml");
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
    card.setTitle("ISS - Visibility from Gaitherburg, Maryland");
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    return SpeechletResponse.newTellResponse(speech, card);
}


/**
 * Creates a {@code SpeechletResponse} for the OneshotCityIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleOneshotCityIntentRequest(final Intent intent, final Session session) {
	
	String cityName = "";
	String cityNameScored = "";
	
	StringBuilder issStrBldr = new StringBuilder();
	
	try {

		cityName = getCityFromIntent(intent, true);
		cityName = WordUtils.capitalizeFully(cityName);
		
		if (cityName.contains(" And ")) {
			cityName = cityName.replaceAll(" And ", " and ");
		}
		
		cityNameScored = cityName;
		
        if (cityName.contains(" ")) {
            cityNameScored = cityName.replaceAll(" ", "_");
        } 
		
		issStrBldr.append("The International Space Station will next be visible from ");
		issStrBldr.append(cityName);
		issStrBldr.append(", Maryland on: ");
		
		URL url = new URL("http://spotthestation.nasa.gov/sightings/xml_files/United_States_Maryland_" + cityNameScored + ".xml");
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
    
    card.setTitle("ISS - Visibility from " + cityName + ", Maryland");
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    return SpeechletResponse.newTellResponse(speech, card);
}


/**
 * Gets the city from the intent, or throws an error.
 */
private String getCityFromIntent(final Intent intent, final boolean assignDefault) throws Exception {
	
    Slot citySlot = intent.getSlot(SLOT_CITY);
    String cityObject = null;
    // slots can be missing, or slots can be provided but with empty value.
    // must test for both.
    if (citySlot == null || citySlot.getValue() == null) {
        if (!assignDefault) {
            throw new Exception("");
        } else {
            // For sample skill, default to Seattle.
            cityObject = "Gaithersburg";
        }
    } else {
        // lookup the city. Sample skill uses well known mapping of a few known cities to
        // station id.
        cityObject = citySlot.getValue().trim();
    }
    return cityObject;
}


/**
 * Creates a {@code SpeechletResponse} for the OneshotCityIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCityStateIntentRequest(final Intent intent, final Session session) {

    String cityObject = null;
    String stateObject = null;
	StringBuilder cardStrBldr = new StringBuilder();
	StringBuilder issStrBldr = new StringBuilder();
	
	try {

	    Slot citySlot = intent.getSlot(SLOT_CITY);
	    Slot stateSlot = intent.getSlot(SLOT_STATE);
	    KeyValuePair statePair = null;
	    
	    if (stateSlot == null || stateSlot.getValue() == null) {
            throw new Exception("");
	    } else {
	    	// lookup the city. Sample skill uses well known mapping of a few known cities to
	    	// station id.
	    	stateObject = stateSlot.getValue().trim();
	    }		
	    
	    if (citySlot == null || citySlot.getValue() == null) {
	            throw new Exception("");
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
		
		issStrBldr.append("The International Space Station will next be visible from ");
		issStrBldr.append(cityObject);
		issStrBldr.append(", " + stateObject + " on: ");
		
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
    
    card.setTitle("ISS - Visibility from " + cityObject + ", " + stateObject);
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    return SpeechletResponse.newTellResponse(speech, card);
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
	    	throw new Exception("");
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

		cityStrBldr.append("<speak><p>Locations in " + statePair.getKey() + " I can give visibility information for are:</p>");
		cardStrBldr.append("Locations in " + statePair.getKey() + "I can give visibility information for are:\n");	    
	    
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

/**
 * Creates a {@code SpeechletResponse} for the StateListIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleStateListIntentRequest() {

	StringBuilder stateStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();
	
	stateStrBldr.append("<speak><p>States that have sighting location information are:</p>");
	cardStrBldr.append("States that have sighting location information are:\n");
	
	try {

		
		for(KeyValuePair item : STATE_LOOKUP) {

			
			String key = item.getKey();
		    
			stateStrBldr.append("<s>" + key + "</s>");
			cardStrBldr.append(key + "\n");
		}

	}
	catch (Exception ex) {
		System.out.println("Exeption" + ex.getMessage());
	}

	stateStrBldr.append("</speak>");
    String speechText = stateStrBldr.toString();
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("ISS - State List");
    card.setContent(cardStrBldr.toString());

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(speechText);

    return SpeechletResponse.newTellResponse(smlspeech, card);
}


/**
 * Creates and returns a {@code SpeechletResponse} with a welcome message.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse getWelcomeResponse() {
	
	StringBuffer welStrBuf = new StringBuffer();
	welStrBuf.append("Welcome to the Space Station.\n");
	welStrBuf.append("Currently I can provide visibility information for the International Space Station from locations in Maryland.\n");
	welStrBuf.append("I can also provide a list of current crew members aboard the International Space Station.\n");
	welStrBuf.append("For a list of visibility locations in Maryland ask to list cities.\n");
	welStrBuf.append("For visibility information for a location ask when it is next visible for the location.\n");
	welStrBuf.append("For a list of crew members ask who is the current crew.\n");
    String speechText = welStrBuf.toString();

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

private SpeechletResponse handleHelpRequest() {
	
	StringBuffer welStrBuf = new StringBuffer();
	welStrBuf.append("Welcome to the Space Station.\n");
	welStrBuf.append("Currently I can provide visibility information for the International Space Station from locations in Maryland.\n");
	welStrBuf.append("I can also provide a list of current crew members aboard the International Space Station.\n");
	welStrBuf.append("For a list of visibility locations in Maryland ask to list cities.\n");
	welStrBuf.append("For visibility information for a location ask when it is next visible for the location.\n");
	welStrBuf.append("For a list of crew members ask who is the current crew.\n");
    String speechText = welStrBuf.toString();
    
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