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

import org.apache.commons.lang3.text.WordUtils;

/**
* @author Christopher Bowerman
* @version 1.4
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
private static final String SLOT_COUNTRY = "Country";
private static final String SLOT_LETTER = "FirstLetter";

private static final String COUNTRY_UNKNOWN = "COUNTRY_UNKNOWN";
private static final String COUNTRY_LIST = "COUNTRY_LIST";
private static final String STATE_UNKNOWN = "STATE_UNKNOWN";
private static final String STATE_LIST = "STATE_LIST";
private static final String CITY_UNKNOWN = "CITY_UNKNOWN";
private static final String CITY_LIST = "CITY_LIST";

static SpaceStationListLoader ssListLoader = new SpaceStationListLoader();

private static final List<KeyValuePair> STATE_LOOKUP = ssListLoader.loadStateInfo();
private static final List<KeyValuePair> COUNTRY_LOOKUP = ssListLoader.loadCountryInfo();

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

    log.debug("Check intent");
    
    if ("CountryListIntent".equals(intentName)) {
    	return handleCountryListIntentRequest(intent, session); 
    } else if ("StateListIntent".equals(intentName)) {
    	return handleStateListIntentRequest(intent, session);
    } else if ("CityListIntent".equals(intentName)) {
    	return handleCityListIntentRequest(intent, session);
    } else if ("CountryLocationListIntent".equals(intentName)) {
    	return handleCountryLocationListIntentRequest(intent, session);
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
    	log.info("Throwing invalid Intent");
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
 * Creates a {@code SpeechletResponse} for the CountryListIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCountryListIntentRequest(final Intent intent, final Session session) {

    return handleCountryList(intent, session, COUNTRY_LIST);
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
		cityStrBldr.append("<p>To list locations a state or region is required.</p>");
		cityStrBldr.append("<p>For a list of locations in a certain state say list locations in Maryland or the name of another state.</p>");
		cityStrBldr.append("<p>Shorten the list by saying list locations in Maryland starting with A or another letter.</p>");
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


/**
 * Creates a {@code SpeechletResponse} for the CityListIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCountryLocationListIntentRequest(final Intent intent, final Session session) {
	
	Slot countrySlot = intent.getSlot(SLOT_COUNTRY);
	Slot locationSlot = intent.getSlot(SLOT_CITY);
	
	if ((countrySlot == null || countrySlot.getValue() == null) && (locationSlot == null || locationSlot.getValue() == null)) {
		
		StringBuilder locationStrBldr = new StringBuilder();
		
		locationStrBldr.append("<speak>");
		locationStrBldr.append("<p>To list locations a country is required.</p>");
		locationStrBldr.append("<p>For a list of locations in a certain country say list locations in France or the name of another country.</p>");
		locationStrBldr.append("<p>Shorten the list by saying list locations in France starting with A or another letter.</p>");
		locationStrBldr.append("</speak>");
	    
	    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
	    ssmlspeech.setSsml(locationStrBldr.toString());
	    
	    StringBuilder rpStrBldr = new StringBuilder();
		rpStrBldr.append("<speak>");
		rpStrBldr.append("For a lists of countries say list countries starting with A or another letter.");
		rpStrBldr.append("</speak>");

	    SsmlOutputSpeech rpssmlspeech = new  SsmlOutputSpeech();
	    rpssmlspeech.setSsml(rpStrBldr.toString());

	    // Create reprompt
	    Reprompt reprompt = new Reprompt();
	    reprompt.setOutputSpeech(rpssmlspeech);	    
	    
	    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt);
	}

	return handleCountryLocationList(intent, session, CITY_LIST);
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
		stateStrBldr.append("<p>For a full list of states or regions say list states.</p>");
		stateStrBldr.append("<p>Shorten the list by saying list states starting with A or any other letter.</p>");
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
		stateStrBldr.append("<speak>");
		if (shortList) {
			stateStrBldr.append("<p>States or regions starting with " + letterSlot.getValue().toUpperCase().charAt(0) + " that have sighting information are:</p>");
			cardStrBldr.append("States or regions starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) +"\" that have sighting information are:\n");					
		}
		else {
			stateStrBldr.append("<p>States or regions with sighting location information are:</p>");
			cardStrBldr.append("States or regions with sighting location information are:\n");					
		}
	}
	
	int counter = 0;
	for(KeyValuePair item : STATE_LOOKUP) {
			
		String key = item.getKey();
		
		if (shortList) {
			
			if (key.toLowerCase().charAt(0) == letterSlot.getValue().toLowerCase().charAt(0)) {
				stateStrBldr.append("<s>" + key + "</s>");
				cardStrBldr.append(key + "\n");
				counter++;
			}
		}
		else {
			stateStrBldr.append("<s>" + key + "</s>");
			cardStrBldr.append(key + "\n");
			counter++;
		}
	}

	if (counter == 0) {
		
		StringBuilder noStrBldr = new StringBuilder();
		StringBuilder rpStrBldr = new StringBuilder();
		
		noStrBldr.append("<speak>");
		noStrBldr.append("<p>There does not appear to be any regions matching your criteria.</p>");
		noStrBldr.append("<p>For a full list of states or regions say list states.</p>");
		noStrBldr.append("<p>Shorten the list by saying list states starting with A or any other letter.</p>");
		noStrBldr.append("</speak>");
		
		rpStrBldr.append("<speak>");
		rpStrBldr.append("For a lists of states or regions say list states.");
		rpStrBldr.append("</speak>");
	    
	    // Create the plain text output.
	    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
	    smlspeech.setSsml(noStrBldr.toString());
	    
	    // Create reprompt
	    SsmlOutputSpeech rpsmlspeech = new  SsmlOutputSpeech();
	    rpsmlspeech.setSsml(rpStrBldr.toString());
	    Reprompt reprompt = new Reprompt();
	    reprompt.setOutputSpeech(rpsmlspeech);

	    return SpeechletResponse.newAskResponse(smlspeech, reprompt);						
	}
	
	
	
	stateStrBldr.append("<p>You can get a list locations with sighting information within a state by saying "
			+ "list locations in Maryland or the name of some other state.</p>");
	stateStrBldr.append("<p>Shorten the list by saying list locations in Maryland starting with A or another letter.</p>");
	cardStrBldr.append("You can get a list locations with sighting information within a state by saying "
			+ "list locations in Maryland or the name of some other state.\n");		
	cardStrBldr.append("Shorten the list by saying list locations in Maryland starting with A or another letter.\n");
	
	stateStrBldr.append("</speak>");
    String speechText = stateStrBldr.toString();
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    if (option.equals(STATE_UNKNOWN)) {
    	card.setTitle("ISS - Unknown State");	
    }
    else {
    	if (shortList) {
    		card.setTitle("ISS - State/Region starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) + "\"");
    	}
    	else {
    		card.setTitle("ISS - State/Region List");
    	}
    }
    
    card.setContent(cardStrBldr.toString());

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(speechText);
  
    StringBuilder rpStrBldr = new StringBuilder();
    rpStrBldr.append("<speak>");
    rpStrBldr.append("<p>You can get a list locations with sighting information within a state by saying "
			+ "list locations in Maryland or the name of some other state.</p>");
	rpStrBldr.append("<p>Shorten the list by saying list locations in Maryland starting with A or another letter.</p>");
	rpStrBldr.append("</speak>");
	
    Reprompt reprompt = new Reprompt();
    SsmlOutputSpeech rpSpeech = new  SsmlOutputSpeech();
    rpSpeech.setSsml(rpStrBldr.toString());

    reprompt.setOutputSpeech(rpSpeech);
    
    return SpeechletResponse.newAskResponse(smlspeech, reprompt, card);
}


private SpeechletResponse handleCountryList(final Intent intent, final Session session, String option) {
	
	StringBuilder countryStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();

	Slot letterSlot = intent.getSlot(SLOT_LETTER);
	boolean shortList = true;
	
	if (letterSlot == null || letterSlot.getValue() == null) {
		shortList = false;
	}
		
	if (option.equals(STATE_UNKNOWN)) {

		StringBuilder rpStrBldr = new StringBuilder();
		
		countryStrBldr.append("<speak>");
		countryStrBldr.append("<p>The country you specified is unknown.</p>");
		countryStrBldr.append("<p>For a full list of countries say list countries.</p>");
		countryStrBldr.append("<p>Shorten the list by saying list countries starting with A or any other letter.</p>");
		countryStrBldr.append("</speak>");
		
		rpStrBldr.append("<speak>");
		rpStrBldr.append("For a lists of countries say list countries.");
		rpStrBldr.append("</speak>");
	    
	    // Create the plain text output.
	    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
	    ssmlspeech.setSsml(countryStrBldr.toString());

	    SsmlOutputSpeech rpssmlspeech = new  SsmlOutputSpeech();
	    rpssmlspeech.setSsml(rpStrBldr.toString());

	    // Create reprompt
	    Reprompt reprompt = new Reprompt();
	    reprompt.setOutputSpeech(rpssmlspeech);

	    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt);						
		
	}
	else {
		countryStrBldr.append("<speak>");
		if (shortList) {
			countryStrBldr.append("<p>Countries starting with " + letterSlot.getValue().toUpperCase().charAt(0) + " that have sighting information are:</p>");
			cardStrBldr.append("Countries starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) + "\" that have sighting information are:\n");		
		}
		else {
			countryStrBldr.append("<p>Countries with sighting location information are:</p>");
			cardStrBldr.append("Countries with sighting location information are:\n");		
		}
	}
	
	int counter = 0;
	for(KeyValuePair item : COUNTRY_LOOKUP) {
			
		String key = item.getKey();
		
		if (shortList) {
			
			if (key.toLowerCase().charAt(0) == letterSlot.getValue().toLowerCase().charAt(0)) {
				countryStrBldr.append("<s>" + key + "</s>");
				cardStrBldr.append(key + "\n");
				counter++;
			}
		}
		else {
			countryStrBldr.append("<s>" + key + "</s>");
			cardStrBldr.append(key + "\n");
			counter++;
		}
	}

	if (counter == 0) {
		
		StringBuilder noStrBldr = new StringBuilder();
		StringBuilder rpStrBldr = new StringBuilder();
		
		noStrBldr.append("<speak>");
		noStrBldr.append("<p>There does not appear to be a country matching your criteria.</p>");
		noStrBldr.append("<p>For a full list of countries say list countries.</p>");
		noStrBldr.append("<p>Shorten the list by saying list countries starting with A or any other letter.</p>");
		noStrBldr.append("</speak>");
		
		rpStrBldr.append("<speak>");
		rpStrBldr.append("For a lists of countries say list countries.");
		rpStrBldr.append("</speak>");
	    
	    // Create the plain text output.
	    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
	    ssmlspeech.setSsml(noStrBldr.toString());
	    
	    // Create reprompt
	    SsmlOutputSpeech rpssmlspeech = new  SsmlOutputSpeech();
	    rpssmlspeech.setSsml(rpStrBldr.toString());
	    Reprompt reprompt = new Reprompt();
	    reprompt.setOutputSpeech(rpssmlspeech);

	    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt);						
	}
	
	
	
	countryStrBldr.append("<p>You can get a list locations with sighting information within a country by saying "
			+ "list locations in France or the name of some other country.</p>");
	countryStrBldr.append("<p>Shorten the list by saying list locations in France starting with A or another letter.</p>");
	cardStrBldr.append("You can get a list locations with sighting information within a country by saying "
			+ "list locations in France or the name of some other country.\n");		
	cardStrBldr.append("Shorten the list by saying list locations in France starting with A or another letter.\n");
	
	countryStrBldr.append("</speak>");
    String speechText = countryStrBldr.toString();
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    if (option.equals(COUNTRY_UNKNOWN)) {
    	card.setTitle("ISS - Unknown Country");	
    }
    else {
    	if (shortList) {
    		card.setTitle("ISS - Countries starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) + "\"");
    	}
    	else {
    		card.setTitle("ISS - Country List");
    	}
    }
    
    card.setContent(cardStrBldr.toString());

    // Create the text output.
    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
    ssmlspeech.setSsml(speechText);
  
    StringBuilder rpStrBldr = new StringBuilder();
    rpStrBldr.append("<speak>");
    rpStrBldr.append("<p>You can get a list locations with sighting information within a country by saying "
			+ "list locations in France or the name of some other country.</p>");
	rpStrBldr.append("<p>Shorten the list by saying list locations in France starting with A or another letter.</p>");
	rpStrBldr.append("</speak>");
	
    Reprompt reprompt = new Reprompt();
    SsmlOutputSpeech rpSpeech = new  SsmlOutputSpeech();
    rpSpeech.setSsml(rpStrBldr.toString());

    reprompt.setOutputSpeech(rpSpeech);
    
    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt, card);
}



private SpeechletResponse handleCityList(final Intent intent, final Session session, String option) {

	boolean shortList = true;
	Slot stateSlot = null;
	Slot letterSlot = null;
	KeyValuePair statePair = null;
	
	StringBuilder cityStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();
	
	try {

	    stateSlot = intent.getSlot(SLOT_STATE);
	
		letterSlot = intent.getSlot(SLOT_LETTER);
		
		
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
	    
		InputStream in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/states/" + statePair.getValue());
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		if (option.equals(CITY_UNKNOWN)) {
			
			StringBuilder rpStrBldr = new StringBuilder();
			
			cityStrBldr.append("<speak>");
			cityStrBldr.append("<p>The location you specified does not have sighting information available.</p>");
			cityStrBldr.append("<p>For a listing of locations in " + statePair.getKey() + " say list locations in " + statePair.getKey() + ".</p>");
			cityStrBldr.append("<p>Shorten the list by saying list locations in " + statePair.getKey() + " starting with A or another letter.</p>");
			cityStrBldr.append("</speak>");
			
			rpStrBldr.append("<speak>");
			rpStrBldr.append("<p>For a listing of locations in " + statePair.getKey() + " say list locations in " + statePair.getKey() + ".</p>");
			rpStrBldr.append("<p>Shorten the list by saying list locations in " + statePair.getKey() + " starting with A or another letter.</p>");
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
			cityStrBldr.append("<speak>");
			
			if (shortList) {
				cityStrBldr.append("<p>Locations in " + statePair.getKey() + " starting with " + letterSlot.getValue().toUpperCase().charAt(0) + " that have sighting information are:</p>");
				cardStrBldr.append("Locations in " + WordUtils.capitalizeFully(statePair.getKey()) + "starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) + "\" that have sighting information are:\n");		
			}
			else {
				cityStrBldr.append("<p>Locations in " + statePair.getKey() + " that have sighting information are:</p>");
				cardStrBldr.append("Locations in " + WordUtils.capitalizeFully(statePair.getKey()) + " that have sighting information are:\n");						
			}
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
			noStrBldr.append("<p>For a listing of locations in " + statePair.getKey() + " say list locations in " + statePair.getKey() + ".</p>");
			noStrBldr.append("<p>Shorten the list by saying list locations in " + statePair.getKey() + " starting with A or another letter.</p>");
			noStrBldr.append("</speak>");
			
			rpStrBldr.append("<speak>");
			rpStrBldr.append("<p>For a listing of locations in " + statePair.getKey() + " say list locations in " + statePair.getKey() + ".</p>");
			rpStrBldr.append("<p>Shorten the list by saying list locations in " + statePair.getKey() + " starting with A or another letter.</p>");
			rpStrBldr.append("</speak>");
		    
		    // Create the plain text output.
		    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
		    smlspeech.setSsml(noStrBldr.toString());
		    
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
    if (shortList) {
    	card.setTitle("ISS - Location Listing: " + WordUtils.capitalizeFully(statePair.getKey()) + " starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) + "\"");
    }
    else {
    	card.setTitle("ISS - Location Listing: " + WordUtils.capitalizeFully(statePair.getKey()));
    }
    
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


private SpeechletResponse handleCountryLocationList(final Intent intent, final Session session, String option) {
	
	boolean shortList = true;
    Slot countrySlot = null;
	Slot letterSlot = null;
	KeyValuePair countryPair = null;
	
	StringBuilder locationStrBldr = new StringBuilder();
	StringBuilder cardStrBldr = new StringBuilder();
	
	try {

	    countrySlot = intent.getSlot(SLOT_COUNTRY);
		letterSlot = intent.getSlot(SLOT_LETTER);
		
		if (letterSlot == null || letterSlot.getValue() == null) {
			shortList = false;
		}	    
	    
	    String countryObject = null;

	    if (countrySlot == null || countrySlot.getValue() == null) {
	    
	    	return handleCountryList(intent, session, COUNTRY_UNKNOWN);
	    } else {
	    	
	        // lookup the country.
	        countryObject = countrySlot.getValue().trim();
	    }		
		
	    
	    for (KeyValuePair item : COUNTRY_LOOKUP) {
	    	if (item.getKey().toLowerCase().equals(countryObject.toLowerCase())) {
	    		countryPair = item;
	    	}
	    }
	    
	    if ((countryPair == null) || (countryPair.getValue() == null) ) {
	    
	    	return handleCountryList(intent, session, COUNTRY_UNKNOWN);
	    }
	    
		InputStream in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/countries/" + countryPair.getValue());
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		if (option.equals(CITY_UNKNOWN)) {
			
			StringBuilder rpStrBldr = new StringBuilder();
			
			locationStrBldr.append("<speak>");
			locationStrBldr.append("<p>The location you specified does not have sighting information available.</p>");
			locationStrBldr.append("<p>For a listing of locations in " + countryPair.getKey() + " say list locations in " + countryPair.getKey() + ".</p>");
			locationStrBldr.append("<p>Shorten the list by saying list locations in " + countryPair.getKey() + " starting with A or another letter.</p>");
			locationStrBldr.append("</speak>");
			
			rpStrBldr.append("<speak>");
			rpStrBldr.append("<p>For a listing of locations in " + countryPair.getKey() + " say list locations in " + countryPair.getKey() + ".</p>");
			rpStrBldr.append("<p>Shorten the list by saying list locations in " + countryPair.getKey() + " starting with A or another letter.</p>");
			rpStrBldr.append("</speak>");
		    
		    // Create the plain text output.
		    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
		    ssmlspeech.setSsml(locationStrBldr.toString());

		    
		    // Create reprompt
		    SsmlOutputSpeech rpssmlspeech = new  SsmlOutputSpeech();
		    rpssmlspeech.setSsml(rpStrBldr.toString());
		    Reprompt reprompt = new Reprompt();
		    reprompt.setOutputSpeech(rpssmlspeech);

		    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt);						
			
		}
		else {
			locationStrBldr.append("<speak>");

			if (shortList) {
				locationStrBldr.append("<p>Locations in " + countryPair.getKey() + " starting with " + letterSlot.getValue().toUpperCase().charAt(0) + " that have sighting information are:</p>");
				cardStrBldr.append("Locations in " + WordUtils.capitalizeFully(countryPair.getKey()) + "starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) + "\" that have sighting information are:\n");		
			}
			else {
				locationStrBldr.append("<p>Locations in " + countryPair.getKey() + " that have sighting information are:</p>");
				cardStrBldr.append("Locations in " + WordUtils.capitalizeFully(countryPair.getKey()) + " that have sighting information are:\n");						
			}
						
		}		
		
		
		String sCurrentLine = "";
		int counter = 0;
		while ((sCurrentLine = reader.readLine()) != null) {
			String locationArray[] = sCurrentLine.split(",");
			String location = locationArray[0];
			
			if (shortList) {
				
				if (location.toLowerCase().charAt(0) == letterSlot.getValue().toLowerCase().charAt(0)) {
					locationStrBldr.append("<s>" + location + "</s>");
					cardStrBldr.append(location + "\n");
					counter++;
				}
			}
			else {
				locationStrBldr.append("<s>" + location + "</s>");
				cardStrBldr.append(location + "\n");
				counter++;
			}					
		}
		
		// Handle if no locations are returned.
		if (counter == 0) {
			
			StringBuilder noStrBldr = new StringBuilder();
			StringBuilder rpStrBldr = new StringBuilder();
			
			noStrBldr.append("<speak>");
			noStrBldr.append("<p>There does not appear to be any locations matching your criteria.</p>");
			noStrBldr.append("<p>For a listing of locations in " + countryPair.getKey() + " say list locations in " + countryPair.getKey() + ".</p>");
			noStrBldr.append("<p>Shorten the list by saying list locations in " + countryPair.getKey() + " starting with A or another letter.</p>");
			noStrBldr.append("</speak>");
			
			rpStrBldr.append("<speak>");
			rpStrBldr.append("<p>For a listing of locations in " + countryPair.getKey() + " say list locations in " + countryPair.getKey() + ".</p>");
			rpStrBldr.append("<p>Shorten the list by saying list locations in " + countryPair.getKey() + " starting with A or another letter.</p>");
			rpStrBldr.append("</speak>");
		    
		    // Create the plain text output.
		    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
		    ssmlspeech.setSsml(noStrBldr.toString());
		    
		    // Create reprompt
		    SsmlOutputSpeech rpssmlspeech = new  SsmlOutputSpeech();
		    rpssmlspeech.setSsml(rpStrBldr.toString());
		    Reprompt reprompt = new Reprompt();
		    reprompt.setOutputSpeech(rpssmlspeech);

		    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt);						
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

	locationStrBldr.append("<p>You can get sighting information for a location by saying "
			+ "give me visibility for Paris France or some other location and country combination.</p>");
	cardStrBldr.append("You can get sighting information for a location by saying "
			+ "give me visibility for Paris France or some other location and country combination.\n");			
	locationStrBldr.append("</speak>");
        
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    
    if (shortList) {
    	card.setTitle("ISS - Location Listing: " + WordUtils.capitalizeFully(countryPair.getKey()) + " starting with \"" + letterSlot.getValue().toUpperCase().charAt(0) + "\"");
    }
    else {
    	card.setTitle("ISS - Location Listing: " + WordUtils.capitalizeFully(countryPair.getKey()));	
    }
        	
    
    card.setContent(cardStrBldr.toString());

    // Create the ssmloutput text output.
    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
    ssmlspeech.setSsml(locationStrBldr.toString());

    StringBuilder rpStrBldr = new StringBuilder();
	rpStrBldr.append("<speak>");
	rpStrBldr.append("<p>You can get sighting information for a location by saying "
			+ "give me visibility for Paris France or some other location and country combination.</p>");
	rpStrBldr.append("</speak>");
    
    SsmlOutputSpeech rpssmlspeech = new  SsmlOutputSpeech();
    rpssmlspeech.setSsml(rpStrBldr.toString());
    
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(rpssmlspeech);
    
    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt, card);
}

/**
 * Creates a {@code SpeechletResponse} for the OneshotCityIntent.
 *
 * @return SpeechletResponse spoken and visual response for the given intent
 */
private SpeechletResponse handleCityStateIntentRequest(final Intent intent, final Session session) {
	
	log.debug("Entering handleCityStateIntentRequest");
	
	boolean hasCountry = false;
	log.debug("hasCountry is: " + hasCountry);
	
    String cityObject = null;
    String stateObject = null;
    String countryObject = null;
    log.debug("Init city/state/country Objects to null");
    
	StringBuilder issStrBldr = new StringBuilder();
	StringBuilder issCrdBldr = new StringBuilder();
	log.debug("Init StringBuilders");
	
	try {
		
		log.debug("getSlots");
		
	    Slot citySlot = intent.getSlot(SLOT_CITY);
	    Slot stateSlot = intent.getSlot(SLOT_STATE);
	    Slot countrySlot = intent.getSlot(SLOT_COUNTRY);
	    
	    log.debug("Initialized city/state/country Slots");
	    
	    KeyValuePair statePair = null;
	    
	    log.debug("Checking for a country.");
	    
	    if (countrySlot == null || countrySlot.getValue() == null) {
	    	
	    	log.debug("country is null so US is default");
	    	countryObject = "United States";
	    }
	    else {

	    	countryObject = countrySlot.getValue().trim();
	    	hasCountry = true;
	    	log.debug("country is not null: " + countryObject);
	    }
	    
	    if (!(hasCountry)) {
	    	
	    	log.debug("No country so checking state.");

		    if (stateSlot == null || stateSlot.getValue() == null) {

		    	log.debug("No state or country.");

				StringBuilder rpStrBldr = new StringBuilder();
				
				issStrBldr.append("<speak>");
				issStrBldr.append("<p>You have not provided a state or country.</p>");
				issStrBldr.append("<p>For a full list of states within the United States that have sighting information say list states.</p>");
				issStrBldr.append("<p>Shorten the list by saying list states starting with A or any other letter.</p>");
				issStrBldr.append("<p>For a full list of countries outside the United states that have sighting information say list countries.</p>");
				issStrBldr.append("<p>Shorten the list by saying list countries starting with A or any other letter.</p>");
				issStrBldr.append("</speak>");
				
				rpStrBldr.append("<speak>");
				rpStrBldr.append("<p>For a lists of states say list states.</p>");
				rpStrBldr.append("<p>For a lists of countries say list countries.</p>");
				rpStrBldr.append("</speak>");
			    
			    // Create the plain text output.
			    SsmlOutputSpeech ssmlspeech = new  SsmlOutputSpeech();
			    ssmlspeech.setSsml(issStrBldr.toString());

			    SsmlOutputSpeech rpssmlspeech = new  SsmlOutputSpeech();
			    rpssmlspeech.setSsml(rpStrBldr.toString());

			    // Create reprompt
			    Reprompt reprompt = new Reprompt();
			    reprompt.setOutputSpeech(rpssmlspeech);

			    return SpeechletResponse.newAskResponse(ssmlspeech, reprompt);		    	
		    	
		    } else {

		    	stateObject = stateSlot.getValue().trim();
		    	log.debug("There is a state: " + stateObject);
		    }			    	
	    	
	    }
	    else {
	    	
	    	log.debug("Have country so setting stateObject to None.");
	    	stateObject = "None";
	    }
	    
	    log.debug("Checking city");
	    if (citySlot == null || citySlot.getValue() == null) {
	    	
	    	log.debug("City is null so calling handleCity");

	    	if (hasCountry) {
	    		return handleCountryLocationList(intent, session, CITY_UNKNOWN);
	    	}
	    	else {
	    		return handleCityList(intent, session, CITY_UNKNOWN);	
	    	}
	    	
	    } else {
	        // lookup the city. Sample skill uses well known mapping of a few known cities to
	        // station id.
	        cityObject = citySlot.getValue().trim();
	        log.debug("There is a city: " + cityObject);
	    }		
	    
	    
        if (hasCountry) {

        	log.debug("Getting country lookup pair");
    	    for (KeyValuePair item : COUNTRY_LOOKUP) {

    	    	if (item.getKey().toLowerCase().equals(countryObject.toLowerCase())) {
    	    		
    	    		statePair = item;
    	    		log.debug("pair is: " + statePair.getKey() + ", " + statePair.getValue());
    	    	}
    	    }
    	    
    	    if ((statePair == null) || (statePair.getValue() == null) ) {

    	    	log.debug("Could not find the country in the lookup to get pair");
    	    	return handleCountryList(intent, session, COUNTRY_UNKNOWN);
    	    }    	    
        	
        }
        else {

        	log.debug("Getting state lookup pair");
    	    for (KeyValuePair item : STATE_LOOKUP) {

    	    	if (item.getKey().toLowerCase().equals(stateObject.toLowerCase())) {
    	    		
    	    		statePair = item;
    	    		log.debug("pair is: " + statePair.getKey() + ", " + statePair.getValue());
    	    	}
    	    }
    	    
    	    if ((statePair == null) || (statePair.getValue() == null) ) {
    		    
    	    	log.debug("Could not find the country in the lookup to get pair");
    	    	return handleStateList(intent, session, STATE_UNKNOWN);
    	    }    
        }
	    
	    
		if (statePair.getKey().equals("National Parks")) {
			log.debug("Dealing with National Parks");
			issStrBldr.append("<speak>");
			issStrBldr.append("<p>The International Space Station will next be visible from ");
			issStrBldr.append(WordUtils.capitalizeFully(cityObject) + " on: </p>");
			
			issCrdBldr.append("The International Space Station will next be visible from ");
			issCrdBldr.append(WordUtils.capitalizeFully(cityObject) + " on: ");			

		} 
		else {
			log.debug("No need for rewording for National Parks");
			issStrBldr.append("<speak>");
			issStrBldr.append("<p>The International Space Station will next be visible from ");
			issStrBldr.append(WordUtils.capitalizeFully(cityObject));
			if (hasCountry) {
				issStrBldr.append(", " + countryObject + " on: </p>");
			}
			else {
				issStrBldr.append(", " + stateObject + " on: </p>");	
			}
			
			
			issCrdBldr.append("The International Space Station will next be visible from ");
			issCrdBldr.append(WordUtils.capitalizeFully(cityObject));
			if (hasCountry) {
				issCrdBldr.append(", " + countryObject + " on: ");
			}
			else {
				issCrdBldr.append(", " + stateObject + " on: ");	
			}
		}	    
	    
		log.debug("Preparing inputStream");
		InputStream in = null;
		if (hasCountry) {
			in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/countries/" + statePair.getValue());
			log.debug("Got inputStream to: /com/cjbdev/echo/iss/speechAssets/countries/" + statePair.getValue());
		}
		else {
			
			in = getClass().getResourceAsStream("/com/cjbdev/echo/iss/speechAssets/states/" + statePair.getValue());
			log.debug("Got inputStream to: /com/cjbdev/echo/iss/speechAssets/states/" + statePair.getValue());
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		log.debug("Created buffered reader");
		
		List<KeyValuePair> cityList = new ArrayList<KeyValuePair>();
				
		String sCurrentLine = "";
		while ((sCurrentLine = reader.readLine()) != null) {
			String cityArray[] = sCurrentLine.split(",");
			KeyValuePair cityItem = new KeyValuePair(cityArray[0], cityArray[1]);
			log.debug("Reading in city: " + cityItem.getKey() + ", " + cityItem.getValue());
			cityList.add(cityItem);
		}
		
		log.debug("cityList created: " + cityList.size());
		
		KeyValuePair cityPair = null;
		
		log.debug("Run through list to look for: " + cityObject);
		
	    for (KeyValuePair item : cityList) {
	    	log.debug("checking :" + cityObject + " against " + item.getKey());
	    	if (item.getKey().toLowerCase().equals(cityObject.toLowerCase())) {
	    		cityPair = item;
	    		log.debug("*************FOUND IT!! " + cityPair.getKey());
	    	}
	    }
	    
	    if (cityPair == null) {
	    	
	    	return handleCityList(intent, session, CITY_UNKNOWN);
	    }
		
		log.info("Retrieving data for: " + cityPair.getValue());
	    
		URL url = new URL("http://spotthestation.nasa.gov/sightings/xml_files/" + cityPair.getValue() + ".xml");
		HttpURLConnection httpcon = (HttpURLConnection)url.openConnection();

		// Reading the feed
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = input.build(new XmlReader(httpcon));
		List<SyndEntry> entries = feed.getEntries();
		Iterator<SyndEntry> itEntries = entries.iterator();
		
		boolean first = true;
		String firstDesc = "";
		String firstDescMod = "";
		String firstSightDate = "";

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
				firstDesc = descStr;
				firstDescMod = descStrMod;
				firstSightDate = sightDate;
				first = false;
			}
			
		}	
		
		
		firstDesc = firstDesc.replaceAll("\t", "");
		firstDesc = firstDesc.replaceAll("\n", "");
		
		String descStrArray[] = firstDesc.split("<br/>");
		
		StringBuilder sightLine = new StringBuilder();

		for(String dStr : descStrArray) {	
			String dTkn = dStr;
			dTkn = dTkn.trim();
			
			if (dTkn.startsWith("Date:")) {
			
				String sdArray[] = firstSightDate.split(" ");
				String fullMonth = getFullMonth(sdArray[1]);
				String newTkn = dTkn.replace(sdArray[1], fullMonth);
				sightLine.append("<p>" + newTkn + "</p>");
			}
			else if (dTkn.startsWith("Time:")) {
				
				sightLine.append("<p>" + dTkn + "</p>");
			}
			else if (dTkn.startsWith("Duration:")) {
				
				sightLine.append("<p>" + dTkn + "</p>");
			}
			else if (dTkn.startsWith("Maximum:")) {
				
				sightLine.append("<p>" + dTkn + "</p>");
			}
			else if (dTkn.startsWith("Approach:")) {
				
				String sStr[] = dTkn.split("above");
				String abrDir = sStr[1].trim();
				String dirStr = getFullDirection(abrDir);
				sightLine.append("<p>" + sStr[0] + "above " + dirStr + "</p>");
			}
			else if (dTkn.startsWith("Departure:")) {
				
				String sStr[] = dTkn.split("above");
				String abrDir = sStr[1].trim();
				String dirStr = getFullDirection(abrDir);
				sightLine.append("<p>" + sStr[0] + "above " + dirStr + "</p>");
			}
		}
		
		
		issStrBldr.append(sightLine.toString());
		issStrBldr.append("</speak>");
				
		issCrdBldr.append(firstDescMod);	
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
	
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    
    if (hasCountry) {
    	card.setTitle("ISS - Sighting Information: " + WordUtils.capitalizeFully(cityObject) + ", " + WordUtils.capitalizeFully(countryObject));
    }
    else {
    	card.setTitle("ISS - Sighting Information: " + WordUtils.capitalizeFully(cityObject) + ", " + WordUtils.capitalizeFully(stateObject));	
    }
    
    card.setContent(issCrdBldr.toString());

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(issStrBldr.toString());

    // Create the ssmloutput text output.
    SsmlOutputSpeech smlspeech = new  SsmlOutputSpeech();
    smlspeech.setSsml(issStrBldr.toString());
    
	log.debug("Exiting handleCityStateIntentRequest");
    
    return SpeechletResponse.newTellResponse(smlspeech, card);
}

private String getFullDirection(String abrStr) {
		
	if (abrStr.equals("N")) {
		return "North";
	}
	else if (abrStr.equals("NNE")) {
		return "North North West";
	}
	else if (abrStr.equals("NE")) {
		return "North East";
	}
	else if (abrStr.equals("ENE")) {
		return "East North East";
	}
	else if (abrStr.equals("E")) {
		return "East";
	}
	else if (abrStr.equals("ESE")) {
		return "East South East";
	}
	else if (abrStr.equals("SE")) {
		return "South East";
	}
	else if (abrStr.equals("SSE")) {
		return "South South East";
	}
	else if (abrStr.equals("S")) {
		return "South";
	}
	else if (abrStr.equals("SSW")) {
		return "South South West";
	}
	else if (abrStr.equals("SW")) {
		return "South West";
	}
	else if (abrStr.equals("WSW")) {
		return "West South West";
	}
	else if (abrStr.equals("W")) {
		return "West";
	}
	else if (abrStr.equals("WNW")) {
		return "West North West";
	}
	else if (abrStr.equals("NW")) {
		return "North West";
	}
	else if (abrStr.equals("NNW")) {
		return "North North West";
	}
	
	// if doesn't change just use the abbreviation after all
	return abrStr;
}

private String getFullMonth(String abrStr) {
	
	if (abrStr.toLowerCase().equals("jan")) {
		return "January";
	}
	else if (abrStr.toLowerCase().equals("feb")) {
		return "February";
	}
	else if (abrStr.toLowerCase().equals("mar")) {
		return "March";
	}
	else if (abrStr.toLowerCase().equals("apr")) {
		return "April";
	}
	else if (abrStr.toLowerCase().equals("may")) {
		return "May";
	}
	else if (abrStr.toLowerCase().equals("jun")) {
		return "June";
	}
	else if (abrStr.toLowerCase().equals("jul")) {
		return "July";
	}
	else if (abrStr.toLowerCase().equals("aug")) {
		return "August";
	}
	else if (abrStr.toLowerCase().equals("sep")) {
		return "September";
	}
	else if (abrStr.toLowerCase().equals("oct")) {
		return "October";
	}
	else if (abrStr.toLowerCase().equals("nov")) {
		return "November";
	}
	else if (abrStr.toLowerCase().equals("dec")) {
		return "December";
	}
	
	// if doesn't change just use the abbreviation after all
	return abrStr;
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
	welStrBldr.append("<p>I can list the locations in a state by saying list locations in Maryland or the name of another state.</p>");
	welStrBldr.append("<p>Shorten the list by saying list locations in Maryland starting with A or another letter.</p>");
	welStrBldr.append("<p>What would you like to do?</p>");
    welStrBldr.append("</speak>");
	
	rpStrBldr.append("<speak>");	
	rpStrBldr.append("For a listing of locations in a state say list locations in Maryland or the name of another state.");
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
	helpStrBldr.append("<p>Shorten the list by saying list locations in Maryland starting with A or another letter.</p>");
	helpStrBldr.append("<p>Or you can get sighting information by saying give me visibility from Gaithersburg Maryland or another location and state combination.</p>");
	helpStrBldr.append("<p>What would you like to do?</p>");
	helpStrBldr.append("</speak>");
	
	rpStrBldr.append("<speak>");
	rpStrBldr.append("<p>For a listing of locations in a state say list locations in Maryland or the name of another state.</p>");
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