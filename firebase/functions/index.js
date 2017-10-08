var functions = require('firebase-functions');
const ActionsSdkAssistant = require('actions-on-google').ActionsSdkAssistant;


const admin = require('firebase-admin');
	  admin.initializeApp(functions.config().firebase);

var newRand = function() {
	return Math.random().toString().slice(2,6);
}


var users;

admin.database().ref('/users/').on('value', function(s){
	users = s.val();
});











exports.createUserStack = functions.auth.user().onCreate(event => {

	const user = event.data; // The Firebase user.
	const email = user.email; // The email of the user.
	const displayName = user.displayName ? user.displayName : ""; // The display name of the user.
	const photoURL = user.photoURL ? user.photoURL : "";
	const uid = user.uid;

	admin.database().ref('/users/' + uid).set({
		"email": email,
		"displayName": displayName,
		"photoURL": photoURL,
		"uid": uid,
		"createdAt": { '.sv': 'timestamp'},
		"lastActivation": 0
	});

});

exports.userRemoved = functions.auth.user().onDelete(event => {
	// ...
	const user = event.data; // The Firebase user.
	admin.database().ref('/users/' + user.uid).remove();
});






exports.generateNewToken = functions.database.ref('/activate/{pushId}')
    .onWrite(event => {
    	// Block updates
		if (event.data.previous.exists()) {
			return;
		}
		// Exit when the data is deleted.
		if (!event.data.exists()) {
			return;
		}

		// Grab the current value of what was written to the Realtime Database.
		const new_activation = event.data.val();
		console.log(new_activation, '/users/' + new_activation.uid);

		admin.database().ref('/token/').set(newRand());

		admin.database().ref('/users/' + new_activation.uid).child('lastActivation').set({'.sv':'timestamp'});
		admin.database().ref('/users/' + new_activation.uid).child('activations').once('value').then(function(snapshot){
			var activations = snapshot.val() ? snapshot.val() + 1: 1;
			admin.database().ref('/users/' + new_activation.uid).child('activations').set(activations);
		});


		admin.database().ref('/interactions').once('value').then(function(snapshot) {
			var interaction = snapshot.val() ? snapshot.val() : { "total": 0, "manual": 0 };
			interaction.total += 1;
			interaction.manual += new_activation.manual ? 1 : 0;
			admin.database().ref('/interactions').set(interaction);
		});



	});


exports.activateRemoved = functions.database.ref('/activate/{pushId}')
    .onWrite(event => {
      // Prevent first created item.
      if (!event.data.previous.exists()) {
        return;
      }
      // Exit if the data is not deleted.
      if (event.data.exists()) {
        return;
      }

      console.log(event.data.previous);
      admin.database().ref('/released/' + event.params.pushId).set(event.data.previous.val());

	});













function raffle(){

	const users_keys = Object.keys(users);
	//var valid_users = {};
	var valid_users_keys = [];

	for (var idx in users) {
		if (!users[idx].sorteado) {
			valid_users_keys.push(idx);
		}
	}
	console.log("VALID USERS", valid_users_keys);
	if (valid_users_keys.length == 0) //NENHUM USUARIO VALIDO
		return null;


	var raffle_key = valid_users_keys[Math.floor(Math.random() * valid_users_keys.length)];

	var user = users[raffle_key];
	if (user.sorteado) { //} || !user.displayName || user.displayName == "") {
		console.log("USUARIO JA SORTEADO... FAZENDO OUTRO.", user);
		return raffle();
	}

	admin.database().ref('/winners').push(user);

	console.log("NEW SORTEIO: ", user);
	admin.database().ref('/users/' + raffle_key).update({
		sorteado: true,
		sorteado_time: { ".sv": "timestamp"}
	});
	return user;


	// {
	// 	"email": email,
	// 	"displayName": displayName,
	// 	"photoURL": photoURL,
	// 	"uid": uid,
	// 	"createdAt": { '.sv': 'timestamp'},
	// 	"lastActivation": 0
	// });

}



exports.updateUsers = functions.https.onRequest((req, res) => {
	admin.database().ref('/users/').on('value', function(s){
		users = s.val();
	});
});


/**
 * Endpoint which handles requests for a Google Assistant action which asks users to say a number
 * and read out the ordinal of that number.
 * e.g. If the user says "Twelve" the action will say "The ordinal of twelve is twelfth".
 */
exports.raffleUser = functions.https.onRequest((req, res) => {
  const assistant = new ActionsSdkAssistant({request: req, response: res});

  // List of re-prompts that are used when we did not understand a number from the user.
  const reprompts = [
    'You can say <break time="3"/>Yes, sure or go'
  ];

  const actions = [
  	'yes',
  	'sure',
  	'let\s do this',
  	'go'
  ];

  const ending = [
  	'no',
  	'bye'
  ];


  const WELCOME_MESSAGE = `<speak>
        Hi Capivaras! <break time="1"/>
        Do you like to raffle a new swag?.
      </speak>`;


  const actionMap = new Map();

  actionMap.set(assistant.StandardIntents.MAIN, assistant => {
    const inputPrompt = assistant.buildInputPrompt(true, WELCOME_MESSAGE, reprompts
    );
    assistant.ask(inputPrompt);
  });

  actionMap.set(assistant.StandardIntents.TEXT, assistant => {
    const rawInput = assistant.getRawInput();

    if (actions.indexOf(rawInput.toLowerCase()) >= 0) {
    	//https://www.myinstants.com/media/sounds/piao-do-bau-com-musica.mp3
    	//https://estudenaudacity.firebaseapp.com/piao-do-bau-com-musica.wav
    	//https://actions.google.com/sounds/v1/cartoon/magic_chime.ogg

    	var raffled_user = raffle();
    	console.log("SORTEEI ", raffled_user);
    	if(!raffled_user) {
			assistant.tell('Sorry. I can\'t find any user to raffle!');

    	} else {
			var user_displayName = raffled_user.displayName || "NAME NOT DEFINED";

			const message = `<speak><audio src="https://estudenaudacity.firebaseapp.com/piao-do-bau-com-musica.wav"></audio><break time="3"/>
				The user is  <break time="3"/>` +
				user_displayName +
				` <break time="3"/>Whould you like to receive another one?</speak>`;
			
			// sortear usuario e avisar
			const inputPrompt = assistant.buildInputPrompt(true, message, reprompts);
	    	// avisar que não entendeu
	    	assistant.ask(inputPrompt);

    	}



    } else if (ending.indexOf(rawInput.toLowerCase()) >= 0) {
		assistant.tell('Goodbye!');
    } else {
      		const inputPrompt = assistant.buildInputPrompt(true, WELCOME_MESSAGE, reprompts);
			//TODO: avisar que não entendeu
			assistant.ask(inputPrompt);
    }

  });

  assistant.handleRequest(actionMap);

});
