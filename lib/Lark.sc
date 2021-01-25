//
// Lark, a wavetable synth
//
Lark {
  var <server;

  var <default_table;
  var <default_params;

  var <globalDefaults;
  var <globalControls;

  var <modGroup;
  var <voicesGroup;
  var <fxGroup;

  var <>oscA_type;
  var <>oscA_enabled;
  var <>oscA_table;

  var <>oscB_type;
  var <>oscB_enabled;
  var <>oscB_table;

  // var <>envelopes;
  // var <>lfos;

  // Create and initialize a new Lark instance on the given server
  *new { arg server;
    ^super.new.init(server)
  }

  // Initialize class and define server side resources
  init { arg srv;
    server = srv;

    //
    // Define synth(s)
    //

    SynthDef(\lark_adsr, {
      arg out=0, gate=0, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1, i_done=0;
      var sig;

      sig = EnvGen.kr(
        Env.adsr(i_atk, i_decay, i_sus, i_rel),
        gate: gate,
        doneAction: i_done,
      );

      Out.kr(out, sig);
    }).add;

    SynthDef(\lark_sweep, {
      arg out=0, gate=0, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1, i_done=0;
      var sig;

      sig = EnvGen.kr(
        Env([0,1,0.5,0],[i_atk,i_sus,i_rel],[1,0,-1]),
        gate: gate,
        doneAction: i_done,
      );

      Out.kr(out, sig);
    }).add;

    SynthDef(\lark_noise_sweep, {
      arg out=0;
      var sig;

      sig = LFNoise1.kr(2).unipolar;

      Out.kr(out, Clip.kr(sig));
    }).add;

    SynthDef(\lark_osc1, {
      arg out=0,
          hz=300, hzOffset=0, hzMod=0, hzModMult=1.0,
          pos=0, posMod=0, posModMult=1.0, i_startBuf=0, i_numBuf=1;
      var sig, pitch, buffer, wt;

      wt = pos + posMod * posModMult;
      buffer = Clip.kr(i_startBuf + (wt * (i_numBuf - 1)), i_startBuf, i_numBuf - 1);
      pitch = hz + hzOffset + (hzMod * hzModMult);
      sig = LeakDC.ar(VOsc.ar(buffer, freq: pitch));

      Out.ar(out, sig);
    }).add;

    SynthDef(\lark_osc3, {
      arg out=0,
          hz=300, hzOffset=0, hzMod=0, hzModMult=1.0,
          pos=0, posMod=0, posModMult=1.0, i_startBuf=0, i_numBuf=1,
          spread=0.2, spreadHz=0.2;
      var sig, pitch, buffer, detune, wt;

      wt = pos + posMod * posModMult;
      buffer = Clip.kr(i_startBuf + (wt * (i_numBuf - 1)), i_startBuf, i_numBuf - 1);
      pitch = hz + hzOffset + (hzMod * hzModMult);
      detune = LFNoise1.kr(spreadHz!3).bipolar(spread).midiratio;
      sig = LeakDC.ar(VOsc3.ar(buffer, freq1: hz*detune[0], freq2: hz*detune[1], freq3: hz*detune[2]));

      Out.ar(out, sig!2);
    }).add;


    SynthDef(\lark_vca, {
      arg out=0, in=0, amp=0.2, ampMod=1;
      var sig = In.ar(in) * ampMod * amp;
      Out.ar(out, sig!2);
    }).add;


    //
    // Setup global controls and parameter defaults
    //

    default_table = LarkTable.new(srv).load(LarkWaves.default);

    // Control busses for global parameters which drive all voices
    globalDefaults = Dictionary.with(
      \oscA_pos -> 0.0,
      \oscA_level -> -12.dbamp,
      \oscA_offset -> 0.0,

      \oscB_pos -> 0.0,
      \oscB_level -> -12.dbamp,
      \oscB_offset -> 0.0,

      \cutoff -> 1200,
      \resonance -> 0.0,
    );

    globalControls = globalDefaults.keys.collectAs({ arg n;
      n -> Bus.control(server, 1)
    }, Dictionary);

    this.setGlobalControlDefaults;

    modGroup = Group.tail();
    voicesGroup = Group.after(modGroup);
    fxGroup = Group.after(voicesGroup);

    oscA_type = \lark_osc1;
    oscA_enabled = true;
    oscA_table = default_table;

    oscB_type = \lark_osc1;
    oscB_enabled = false;
    oscB_table = default_table;
  }

  free {
    globalControls.values.do({_.free});
    modGroup.free;
    voicesGroup.free;
    fxGroup.free;
  }

  // Sets all global control busses to their default values
  setGlobalControlDefaults {
    globalControls.keys.do({ arg n;
      globalControls[n].set(globalDefaults[n]);
    })
  }

  // Sets the given envelope number to be an ADSR
  // setEnvAdsr {
  //   arg which, attack=0.2, decay=0.3, sustain=0.7, release=0.2;
  //
  //   envelopes[which] = Dictionary.with(
  //     \type -> \adsr_mod,
  //     \params -> [\i_atk, attack, \i_decay, decay, \i_sus, sustain, \i_rel, release],
  //     // NOTE: the above could also just be a dictionary with the synthdef arg names and then call
  //     // Dictionary.getPairs at synth creation time.
  //   );
  // }

  // Play a note
  // noteOn {
  //   arg hz=120, amp=0.1, bufPos=0, rel=0.2, spread=0.2, spreadHz=0.2, atk=0.2, decay=0.3, sus=0.7;
  //   ^if(oscA_enabled, {
  //     Synth(this.oscA_type, [
  //       \hz, hz,
  //       \amp, amp,
  //       \bufPos, bufPos,
  //       \i_buf, this.oscA_table.baseBuf,
  //       \i_numBuf, this.oscA_table.numBuf,
  //       \i_rel, rel,
  //       \spread, spread,
  //       \spread_hz, spreadHz,
  //       \i_atk, atk,
  //       \i_decay, decay,
  //       \i_sus, sus,
  //       \i_rel, rel,
  //     ], this.server);
  //   }, { nil });
  // }

  noteOn2 {
    arg hz, amp;

    ^LarkVoice.new.start(
      this.server,
      this.voicesGroup,
      out: 0,
      oscSpec: this.osc1Spec,
      ampSpec: this.ampSpec,
      modSpecs: [this.posSpec],
      hz: hz,
      amp: amp
    );
  }


  osc1Spec {
    // TODO: for now just construct something
    ^LarkSpec.new(oscA_type, [
      \i_startBuf, oscA_table.baseBuf,
      \i_numBuf, oscA_table.numBuf,
    ]);
  }

  ampSpec {
   ^LarkSpec.new(\lark_adsr, [
      \i_atk, 0.2,
      \i_decay, 0.3,
      \i_sus, 0.7,
      \i_rel, 1.0,
    ]);
  }

  posSpec {
    ^LarkSpec.new(\lark_noise_sweep, []);
  }

  // TODO: methods to set various parameters
}

LarkSpec {
 var <defName;
 var <params;

 *new {
    arg name, defaults;
    ^super.new.init(name, defaults);
  }

  init {
    arg name, defaults;
    defName = name;
    params = Dictionary.newFrom(defaults);
  }

  args {
    ^this.params.asPairs;
  }

  printOn {
    arg stream;
    stream << "LarkSpec(" << defName << ", " << this.args << ")";
  }
}

LarkVoice {
  var <voiceGroup;
  var <voiceBus;

  var <pitchBus;
  var <gateBus;
  var <ampBus;

  var <modBusses;
  var <modSources;

  var <oscA;

  var <ampEnv;
  var <ampSyn;


  start {
    arg server, target, out=0, oscSpec, ampSpec, modSpecs=[], hz=300, amp=0.2;

    Post << "LarkVoice(" << server << ", " << target << ", " << out << ", " << oscSpec << ", " << ampSpec << ", " << hz << ", " << amp << ")\n";

    // create a group to contain the synths for the voice
    voiceGroup = Group.new(target);
    voiceBus = Bus.audio(server, 1);

    // create a voice specific control busses
    pitchBus = Bus.control(server, 1); pitchBus.set(hz);
    gateBus = Bus.control(server, 1);
    ampBus = Bus.control(server, 1);

    modBusses = modSpecs.collectAs({ arg spec, i; Bus.control(server, 1) }, Array);
    modSources = modSpecs.collectAs({ arg spec, i;
      Synth.new(spec.defName, [\out, modBusses[i], \gate, gateBus] ++ spec.args, voiceGroup, \addToHead);
      // m.map(\gate, gateBus);
    }, Array);

    // create secondary envs, mapping gate

    // create osc synths (before mixer), mapping modulators,
    oscA = Synth.new(oscSpec.defName, [\out, voiceBus] ++ oscSpec.args, voiceGroup, \addToTail);
    oscA.map(\hz, pitchBus, \posMod, modBusses[0]);
    //oscA.map(\hz, pitchBus);

    // create filter (after mixer)

    // scale voice ouput by amp env (after filter) and write to main bus
    ampEnv = Synth.new(ampSpec.defName, [\i_done, Done.freeGroup, \out, ampBus] ++ ampSpec.args, voiceGroup, \addToHead);
    ampEnv.map(\gate, gateBus);
    ampSyn = Synth.new(\lark_vca, [\out, out, \in, voiceBus, \amp, amp], voiceGroup, \addToTail);
    ampSyn.map(\ampMod, ampBus);

    gateBus.set(1);

    Post << "   gate: " << gateBus.get << "\n";
    Post << "  pitch: " << pitchBus.get << "\n";
    Post << "    amp: " << amp << "\n";
  }

  stop {
    gateBus.set(0);
  }

  free {
    voiceGroup.freeAll;
  }
}

//
// LarkWaves provides various helper methods to load or generate collections
// of wavetables suitable for passing to LarkTable.
//
LarkWaves {

  // Generate the default (sine) wave
  *default {
    var wave = Signal.sineFill(1024, [1], [0]).asWavetable;
    ^Array.fill(2, { wave })
  }

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

    postln("LarkWaves.fromFile(\"" ++ path ++ "\", " ++ tableSize ++ ")");
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

//
// LarkTable allocates server Buffers and loads waves into the server
//
LarkTable {
  var <server;
  var <buffers;
  var <baseBuf;
  var <numBuf;
  var <numLoaded;

  *fromFile { arg server, path, waveSize;
    ^this.new(server).load(LarkWaves.fromFile(path, waveSize));
  }

  *new { arg server;
    ^super.new.init(server);
  }

  init { arg srv;
    server = srv;
    numLoaded = 0;
  }

  // Load the collection of wavetables into buffers on the server.
  //
  // The provided wavetables are assumed to be power of 2 in size and in
  // wavetable format (see Signal, Wavetable for more detail). Any previously
  // loaded wavetables are freed before loading the new set.
  //
  load { arg waves;
    this.free;    // release any Buffers held by this instance

    numLoaded = 0;

    buffers = Buffer.allocConsecutive(waves.size, server, waves[0].size);
    buffers.do({ arg buf, i;
      buf.loadCollection(waves[i], action: { arg buf; numLoaded = numLoaded + 1; });
    });

    baseBuf = buffers[0].bufnum;
    numBuf = buffers.size - 1;

    postln("LarkTable.load: base = " ++ baseBuf ++ ", num = " ++ numBuf);
  }

  free {
    if(buffers.notNil, {
      buffers.do({ arg buf; buf.free; });
      numLoaded = 0;
      baseBuf = 0;
      numBuf = 0;
    });
  }

  printOn {
    arg stream;
    stream << "LarkTable(" << baseBuf << ", " << numBuf << ", " << buffers << ")\n";
  }
}




/*

~q = LarkTable.new(s).load(LarkWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256));
~q = LarkTable.fromFile(s, "/Applications/WaveEdit/banks/ROM B.wav", 256);
~q.numBuf
~q.numLoaded
~q.free

~f = Lark.new(s);

~f.osc1Spec
~f.ampSpec

~f.loadBuffers(LarkWaves.random());

~w = LarkWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256);
~w = LarkWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/4088.wav", 2048);
~w = LarkWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/Jno.wav", 2048);
~w = LarkWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav", 2048);

~f.loadBuffers(~w);

~f.loadBuffers(LarkWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Digital/SubBass_1.wav", 2048));

~f.loadBuffers(LarkWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/User/AF kaivo test2.wav", 2048));


~f.buffers[0].query;
~f.buffers[0].plot;
~x = ~f.noteOn(80, bufPos: 0, rel: 4);
~x = ~f.noteOn(80, rel: 4, spread: 0.8, spreadHz: 0.17);
~x = ~f.noteOn(440, rel: 4, spread: 0.14, spreadHz: 0.03);
~x = ~f.noteOn(220, rel: 4, spread: 0, spreadHz: 0);
~x = ~f.noteOn(120, rel: 4, spread: 0.14, spreadHz: 0.03);

~x = ~f.noteOn(40, rel: 4, spread: 0.14, spreadHz: 0.03);
~x.free;
~x.set(\gate, 0);

~y = ~f.noteOn(120, rel: 4, spread: 0.14, spreadHz: 0.03);
~y.free;
~y.set(\gate, 0);

~z = ~f.noteOn(80, rel: 4, spread: 0.14, spreadHz: 0.03);
~z.free;
~z.set(\gate, 0);


~wt = LarkWaves.random();
~w.do({arg w, i; w.plot(i.asString)});


~wt.size;
~wt[0].plot;
~wt[1].plot;



*/