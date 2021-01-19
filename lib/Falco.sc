// Falco, a wavetable synth
//
Falco {
  var <server;
  var <buffers;
  var <bufLo;
  var <bufNum;

  // Create and initialize a new Falco instance on the given server
  *new { arg server;
    ^super.new.init(server)
  }

  // Initialize class and define server side resources
  init { arg srv;
    server = srv;

    SynthDef(\falco, {
      arg out=0, hz=300, gate=1, amp=0.3, i_atk=0.2, i_sus=2, i_rel=1,
          i_buf=0, i_bufNum=1, bufPos=0;
      var sig, pos, env, detune;

      env = EnvGen.kr(Env([0,1,1,0],[i_atk,i_sus,i_rel],[1,0,-1]),
                      gate,
                      levelScale:amp,
                      doneAction:2);
      bufPos = Line.kr(0, 1, i_sus + i_rel);
      pos = LinLin.kr(bufPos, 0, 1.0, i_buf, i_bufNum);
      detune = LFNoise1.kr(0.2!3).bipolar(0.2).midiratio;

      //pos = LFNoise1.kr(0.5).range(0, i_bufNum - 1);
      sig = VOsc.ar(pos, hz*detune) * env;
      sig = Splay.ar(sig);
      sig = LeakDC.ar(sig);

      Out.ar(out, sig!2);
    }).add;

    //server.sync;
  }

  // Load the collection of wavetables into buffers on the server.
  //
  // The provided wavetables are assumed to be power of 2 in size and in
  // wavetable format (see Signal, Wavetable for more detail). Any previously
  // loaded wavetables are freed before loading the new set.
  //
  loadBuffers { arg waves;

    if(buffers.notNil, {
      buffers.free;
    });

    buffers = Buffer.allocConsecutive(waves.size, server, waves[0].size);
    buffers.do({
      arg buf, i;
      buf.loadCollection(waves[i]);
    });

    bufLo = buffers[0].bufnum;
    bufNum = buffers.size;

    postln("Falco.loadBuffers: low = " ++ bufLo ++ ", num = " ++ bufNum);
  }

  // Play a note
  noteOn {
    arg hz=120, amp=0.1, bufPos=0, rel=1;
    ^Synth(\falco, [
      \hz, hz,
      \amp, amp,
      \bufPos, bufPos,
      \i_buf, bufLo,
      \i_bufNum, bufNum,
      \i_rel, rel,
    ]);
  }


  // TODO: methods to set various parameters
}


// FalcoWaves provides various helper methods to load or generate collections
// of wavetables suitable for passing to Falco.loadBuffers.
//
FalcoWaves {

  // Generate a collection of 4 randomly shaped wavetables
  *random {
    ^Array.fill(4, {
      var numSegs = rrand(4, 20);
      Env(
        [0]++
        (({rrand(0.0, 1.0)}!(numSegs-1)) * [1, -1]).scramble
        ++[0],
        {exprand(1,20)}!numSegs,
        {rrand(-20,10)}!numSegs
      ).asSignal(1024).asWavetable;
    });
  }

  // Load a collection of concatenated wavetables from the given file.
  //
  // Reads the given file sound file splitting into tableSize chunks and
  // converting it to wavetable format. It is assumed that tableSize is a power
  // of 2.
  //
  *fromFile {
    arg path, tableSize;

    var f = SoundFile.openRead(path);
    var raw = Signal.newClear(f.numFrames);
    var numWaves = f.numFrames.div(tableSize);
    var waves = Array.newClear(numWaves);
    var offset = 0;

    postln("FalcoWaves.fromFile(\"" ++ path ++ "\", " ++ tableSize ++ ")");
    postln("  numWaves = " ++ numWaves);
    postln("  extra = " ++ f.numFrames.mod(tableSize));
    // TODO: check waveSize is power of 2
    // TODO: check numFrames is a multiple of waveSize

    f.readData(raw);
    numWaves.do({
      arg i;
      var offset = i * tableSize;
      waves[i] = raw[offset..(offset + tableSize - 1)].asWavetable;
    });

    ^waves;
  }

}



/*

~f = Falco.new(s);
~f.loadBuffers(FalcoWaves.random());

~w = FalcoWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256);
~w = FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/4088.wav", 2048);
~w = FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/Jno.wav", 2048);
~w = FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav", 2048);

~f.loadBuffers(~w);

~f.buffers[0].query;
~f.buffers[0].plot;
~x = ~f.noteOn(80, bufPos: 0, rel: 4);
~x.free

~wt = FalcoWaves.random();
~w.do({arg w, i; w.plot(i.asString)});


~wt.size;
~wt[0].plot;
~wt[1].plot;

*/