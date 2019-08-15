RealTimeEventStreamPlayer {

	var patternDur = 4, baseBarBeat, <>sequence, <>pattern, <>stream, <>player;

	*new {
		^super.new.init();
	}

	init {
		this.sequence = LinkedList.new;
		[
			(degree: 0, time: 0, sustain: 0.2),
			(degree: 0, time: 2, sustain: 0.2)
		].do { |item| sequence.add(item) };

		// can't just Pseq because we need to account for insertions
		this.pattern = Prout { |inval|

			//Starting variables
			var item, node,
			clock = TempoClock,
			barline = clock.nextBar,
			nextTime, delta;
			baseBarBeat = barline;

			if (sequence.size > 0, {
				//start with the first node in the sequence
				node = sequence.nodeAt(0);
				while {
					//While we have items in the sequence do this loop (infinite)
					item = node.tryPerform(\obj);
					item.notNil
				} {
					//Out next event's start-time
					nextTime = barline + item[\time];

					//if this is bigger then current beats this means we are in the start of our loop ->
					//so we yield silence till the first event starts
					if(clock.beats < nextTime) {
						inval = Event.silent(nextTime - clock.beats).yield;
					};

					// now we arrived at the event - we subtract its time with the next event's time to get the duration
					// if it's last in sequence we substract it with the 'next-bar' time a.k.a. sequence length
					if(node.next.notNil) {
						delta = node.next.obj[\time] - item[\time];
					} {
						delta = patternDur - item[\time];
					};

					//we need to update the 'next-bar' variable once we will reach the end of the current bar-sequence
					if(clock.beats + delta - barline >= patternDur) {
						barline = barline + patternDur;
						baseBarBeat = barline;
					};

					//this creates the actual event and puts it into the Pattern
					inval = item.copy.put(\dur, delta).yield;

					//continue the loop
					node = node.next;
					if(node.isNil) { node = sequence.nodeAt(0) };
				}
			});
		};

		this.stream = pattern.asStream;
		this.player = EventStreamPlayer(stream).play(doReset: true);
	}

	getBeats {
		var clock = TempoClock;
		^clock.beats
	}

	// functions to change sequence
	reschedule { |time|
		var phaseNow, reschedTime, nextNodeToPlay, clock;

		//this gives us the phase of the current bar [0-patternLength]
		phaseNow = (player.clock.beats - baseBarBeat) % patternDur;

		//we search for the next node to play  in the sequence given the current phase
		nextNodeToPlay = sequence.detect { |item| item[\time] >= phaseNow };

		//if we found one we take its time-value as a starting-point to schedule our next player
		if(nextNodeToPlay.notNil) {
			reschedTime = baseBarBeat + nextNodeToPlay[\time];
		} {
			//if not we just take the next bar
			reschedTime = (player.clock.beats - baseBarBeat).roundUp(patternDur);
		};

		//swap out stream players
		clock = player.clock;
		player.stop;
		player = EventStreamPlayer(stream);
		clock.schedAbs(reschedTime, player.refresh);
	}

	insertNote { |time, sustain|
		var node, new;

		//search for place to insert
		node = sequence.nodeAt(0);
		while {
			node.notNil and: { node.obj[\time] < time }
		} {
			node = node.next;
		};

		//we found the place to insert our new node
		new = LinkedListNode((sustain: sustain, time: time));
		if(node.notNil) {
			// change A --> C into A --> B --> C; B = new; C = node
			new.prev = node.prev;  // B <-- A
			node.prev.next = new;  // A --> B
			new.next = node;  // B --> C
			node.prev = new;  // C <-- B
		} {
			new.prev = sequence.last;  // add at the end
			sequence.last.next = new;
		};

		//we do not need the time argument?
		this.reschedule.(time);
	}

	deleteNote { |time, sustain|
		var node, next;

		// search for node to delete
		node = sequence.nodeAt(0);
		while {
			node.notNil and: {
				node.obj[\time] != time and: { node.obj[\sustain] != sustain }
			}
		} {
			node = node.next;
		};
		if(node.notNil) {
			next = node.next;
			next.prev = node.prev;
			node.prev.next = next;
		};
		// not really necessary to reschedule
		// the Prout will add rests automatically
		// if the next note to play was deleted
	}
}


// hit this during beat 2
// ~insertNote.(1.75, 8);

/*got item: [ 1420.0, ( 'degree': 0, 'time': 0 ) ]
got item: [ 1421.0, ( 'degree': 1, 'time': 1 ) ]
phaseNow: 1.292918184
nextToPlay: ( 'degree': 8, 'time': 1.75 )
reschedTime: 1421.75
-> a TempoClock
// YES, plays at the right time
got item: [ 1421.75, ( 'degree': 8, 'time': 1.75 ) ]
got item: [ 1422.0, ( 'degree': 2, 'time': 2 ) ]*/

// hit this during beat 2
//~deleteNote.(1.75, 8);
/*
got item: [ 1428.0, ( 'degree': 0, 'time': 0 ) ]
got item: [ 1429.0, ( 'degree': 1, 'time': 1 ) ]
-> a LinkedListNode
// thread wakes up at 1429.75,
// and skips over the deleted note
rest: ( 'dur': Rest(0.25), 'delta': 0.25 )
got item: [ 1430.0, ( 'degree': 2, 'time': 2 ) ]*/
