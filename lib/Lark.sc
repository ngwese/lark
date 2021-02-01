
LarkDefs {

}

//
// Lark, a wavetable synth
//
Lark {
  var <server;

  var <parentGroup;
  var <outBus;

  var <defaultTable;
  var <randomTable;

  var <globalDefaults;
  var <globalControls;

  var <modGroup;
  var <voicesGroup;
  var <fxGroup;

  var <>osc1_type;
  var <>osc1_enabled;
  var <>osc1_table;
  var <osc1_mappings;

  var <>osc2_type;
  var <>osc2_enabled;
  var <>osc2_table;
  var <osc2_mappings;

  var <>sub_enabled;
  var <sub_mappings;

  var <>noise_type;
  var <>noise_enabled;
  var <noise_mappings;

  var <voices;
  var <voiceStarted;

  // var <>envelopes;
  // var <>lfos;

  // Create and initialize a new Lark instance on the given server
  *new { arg server, group, out=0;
    ^super.new.init(server, group, out);
  }

  *definitions {
    var defs = List.new;

    defs.add(
      SynthDef(\lark_adsr, {
        arg out=0, gate=0, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1, i_done=0;
        var sig;

        sig = EnvGen.kr(
          Env.adsr(i_atk, i_decay, i_sus, i_rel),
          gate: gate,
        );

        Out.kr(out, sig);
      })
    );

    defs.add(
      SynthDef(\lark_sweep, {
        arg out=0, gate=0, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1, i_done=0;
        var sig;

        sig = EnvGen.kr(
          Env([0,1,0.5,0],[i_atk,i_sus,i_rel],[1,0,-1]),
          gate: gate,
          // doneAction: i_done,
        );

        Out.kr(out, sig);
      })
    );

    defs.add(
      SynthDef(\lark_noise_sweep, {
        arg out=0;
        var sig;

        sig = LFNoise1.kr(2).unipolar;

        Out.kr(out, Clip.kr(sig));
      })
    );

    defs.add(
      SynthDef(\lark_sine, {
        arg out=0, hz=300, hzRatio=1.0, hzMod=0, hzModMult=1.0, level=1.0;
        var sig, pitch;

        pitch = (hz * hzRatio) + (hzMod * hzModMult);
        sig = SinOsc.ar(hz, mul: 0.3);

        Out.ar(out, sig * level);
      })
    );


    defs.add(
      SynthDef(\lark_osc1, {
        arg out=0,
            hz=300, hzRatio=1.0, hzMod=0, hzModMult=1.0, level=1.0,
            pos=0, posMod=0, posModMult=1.0, i_startBuf=0, i_numBuf=1;
        var sig, pitch, buffer, wt;

        wt = pos + posMod * posModMult;
        buffer = Clip.kr(i_startBuf + (wt * (i_numBuf - 0.001)), i_startBuf, i_numBuf - 0.001);
        pitch = (hz * hzRatio) + (hzMod * hzModMult);
        sig = LeakDC.ar(VOsc.ar(buffer, freq: pitch, mul: 0.3));

        Out.ar(out, sig * level);
      })
    );

    defs.add(
      SynthDef(\lark_osc3, {
        arg out=0,
            hz=300, hzRatio=1.0, hzMod=0, hzModMult=1.0, level=1.0,
            pos=0, posMod=0, posModMult=1.0, i_startBuf=0, i_numBuf=1,
            spread=0.2, spreadHz=0.2;
        var sig, pitch, buffer, detune, wt;

        wt = pos + posMod * posModMult;
        buffer = Clip.kr(i_startBuf + (wt * (i_numBuf - 0.001)), i_startBuf, i_numBuf - 0.001);
        pitch = (hz * hzRatio) + (hzMod * hzModMult);
        detune = LFNoise1.kr(spreadHz!3).bipolar(spread).midiratio;
        sig = LeakDC.ar(VOsc3.ar(buffer, freq1: hz*detune[0], freq2: hz*detune[1], freq3: hz*detune[2], mul: 0.3));

        Out.ar(out, sig * level);
      })
    );

    defs.add(
      SynthDef(\lark_pulse, {
        arg out=0, hz=300, hzRatio=1.0, hzMod=0, hzModMult=1.0, level=1.0,
            width=0.5, widthMod=0, widthModMult=1.0;
        var sig, pitch, pw;

        pw = width + (widthMod * widthModMult);
        pitch = (hz * hzRatio) + (hzMod * hzModMult);
        sig = PulseDPW.ar(pitch, pw, mul: 0.3);

        Out.ar(out, sig * level);
      })
    );

    defs.add(
      SynthDef(\lark_white_noise, {
        arg out=0, level=1.0;
        Out.ar(out, WhiteNoise.ar(level));
      })
    );

    defs.add(
      SynthDef(\lark_vca, {
        arg out=0, in=0, amp=0.2, ampMod=1;
        var sig = In.ar(in) * ampMod * amp;
        Out.ar(out, sig!2);
      })
    );

    ^defs;
  }


  // Initialize class and define server side resources
  init { arg argServer, argGroup, argOut;
    var serverInitialized = Condition.new;

    server = argServer;
    parentGroup = if(argGroup.isNil, {server}, {argGroup});
    outBus = argOut;

    modGroup = Group.new(parentGroup);
    voicesGroup = Group.after(modGroup);
    fxGroup = Group.after(voicesGroup);

    voices = Array.new;

    Post << "Lark.init: server = " << server << ", xg = " << parentGroup << ", out = " << outBus << "\n";

    Routine.run({
      Post << "...sending defs\n";
      Lark.definitions.do({ arg d;
        d.send(server);
      });

      Post << "...sending tables\n";
      defaultTable = LarkTable.new(server).load(LarkWaves.default);
      randomTable = LarkTable.new(server).load(LarkWaves.random);

      server.sync;

      serverInitialized.test = true;
      serverInitialized.signal;
      Post << "...serverSynced\n";
    });

    // Control busses for global parameters which drive all voices (non per-note parameters)
    globalDefaults = IdentityDictionary.with(
      \osc1_pos -> 0.0,
      \osc1_level -> 0.dbamp,
      \osc1_tune -> 0.0.midiratio,  // as ratio of pitch, -12.midiratio for -12 semitones

      \osc2_pos -> 0.0,
      \osc2_level -> 0.dbamp,
      \osc2_tune -> 0.0.midiratio,

      \sub_level -> -12.dbamp,
      \sub_tune -> -12.midiratio,

      \noise_level -> -24.dbamp,

      \cutoff -> 1200,
      \resonance -> 0.0,
    );

    globalControls = globalDefaults.keys.collectAs({ arg n;
      n -> Bus.control(server, 1)
    }, IdentityDictionary);

    this.setControlDefaults;

    Routine.run({
      // wait for defs and default buffers to land
      serverInitialized.wait;

      Post << "...setting default configuration\n";

      // default configuration
      osc1_type = \lark_osc3;
      osc1_enabled = true;
      osc1_table = randomTable;
      osc1_mappings = [
        \level, globalControls[\osc1_level],
        \pos, globalControls[\osc1_pos],
        \hzRatio, globalControls[\osc1_tune],
      ];

      osc2_type = \lark_osc3;
      osc2_enabled = true;
      osc2_table = defaultTable;
      osc2_mappings = [
        \level, globalControls[\osc2_level],
        \pos, globalControls[\osc2_pos],
        \hzRatio, globalControls[\osc2_tune],
      ];

      sub_enabled = true;
      sub_mappings = [
        \level, globalControls[\sub_level],
        \hzRatio, globalControls[\sub_tune],
      ];

      noise_type = \lark_white_noise;
      noise_enabled = false;
      noise_mappings = [
        \level, globalControls[\noise_level],
      ];

      Post << "...allocating voices\n";
      // allocate voices
      this.allocVoices(4);

      Post << "...voices: " << this.voices << "\n";
    });
  }

  free {
    globalControls.values.do({_.free});
    modGroup.free;
    voicesGroup.free;
    fxGroup.free;
  }

  // Sets all global control busses to their default values
  setControlDefaults {
    globalControls.keys.do({ arg n;
      globalControls[n].setSynchronous(globalDefaults[n]);
    })
  }

  setControl { arg name, value;
    globalControls[name].set(value);
  }

  /*
  noteOn {
    arg hz, amp;

    ^LarkVoice.new.start(
      this.server,
      this.voicesGroup,
      out: this.outBus,
      osc1Spec: if(osc1_enabled, {this.osc1Spec}),
      osc2Spec: if(osc2_enabled, {this.osc2Spec}),
      oscSubSpec: if(sub_enabled, {this.oscSubSpec}),
      oscNoiseSpec: if(noise_enabled, {this.oscNoiseSpec}),
      ampSpec: this.ampSpec,
      modSpecs: [this.posSpec],
      hz: hz,
      amp: amp
    );
  }
  */

  allocVoices {
    arg n;

    if(voices.notNil, {
      voices.do({arg v; v.free });
    });

    voices = n.collectAs({ LarkVoice.new(
      this.server,
      this.voicesGroup,
      out: this.outBus,
      osc1Spec: if(osc1_enabled, {this.osc1Spec}),
      osc2Spec: if(osc2_enabled, {this.osc2Spec}),
      oscSubSpec: if(sub_enabled, {this.oscSubSpec}),
      oscNoiseSpec: if(noise_enabled, {this.oscNoiseSpec}),
      ampSpec: this.ampSpec,
      modSpecs: [this.posSpec],
    ) }, Array);

    voiceStarted = Array.fill(n, { false });
  }

  voiceCount {
    ^voices.size;
  }

  start {
    arg n=0, hz=300, amp=0.2;
    voices[n].start(hz, amp);
    voiceStarted[n] = true;
  }

  stop {
    arg n=0;
    voices[n].stop;
    voiceStarted[n] = false;
  }

  osc1Spec {
    ^LarkSpec.new(osc1_type, [
      \i_startBuf, osc1_table.baseBuf,
      \i_numBuf, osc1_table.numBuf,
    ], osc1_mappings);
  }

  osc2Spec {
    ^LarkSpec.new(osc2_type, [
      \i_startBuf, osc2_table.baseBuf,
      \i_numBuf, osc2_table.numBuf,
    ], osc2_mappings);
  }

  oscSubSpec {
    ^LarkSpec.new(\lark_pulse, mappings: sub_mappings);
  }

  oscNoiseSpec {
    ^LarkSpec.new(noise_type, mappings: noise_mappings);
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
  var <paramMappings;

  *new {
    arg name, defaults=[], mappings=[];
    ^super.new.init(name, defaults, mappings);
  }

  init {
    arg name, defaults, mappings;
    defName = name;
    params = Dictionary.newFrom(defaults);
    paramMappings = mappings;
  }

  args {
    ^this.params.asPairs;
  }

  printOn {
    arg stream;
    stream << "LarkSpec(" << defName << ", params: " << this.args << ", mappings: " << paramMappings << ")";
  }
}

LarkVoice {
  var <voiceGroup;
  var <voiceBus;

  var <pitchBus;
  var <gateBus;
  var <ampBus;
  var <ampScaleBus;

  var <modBusses;
  var <modSources;

  var <osc1;
  var <osc2;
  var <oscSub;
  var <oscNoise;

  var <ampEnv;
  var <ampSyn;

  *new {
    arg server, target, out=0, osc1Spec, osc2Spec, oscSubSpec, oscNoiseSpec, ampSpec, modSpecs=[];
    ^super.new.init(server, target, out, osc1Spec, osc2Spec, oscSubSpec, oscNoiseSpec, ampSpec, modSpecs);
  }

  init {
    arg server, target, out=0, osc1Spec, osc2Spec, oscSubSpec, oscNoiseSpec, ampSpec, modSpecs=[];

    Post << "LarkVoice.init(" << server << ", " << target << ", " << out << ", "
    << [osc1Spec, osc2Spec, oscSubSpec, oscNoiseSpec] << ", " << ampSpec << ")\n";

    // create a group to contain the synths for the voice
    voiceGroup = Group.new(target);
    voiceBus = Bus.audio(server, 1);

    // create a voice specific control busses
    pitchBus = Bus.control(server, 1);
    gateBus = Bus.control(server, 1);
    ampBus = Bus.control(server, 1);
    ampScaleBus = Bus.control(server, 1);

    // create secondary envs, mapping gate
    modBusses = modSpecs.collectAs({ arg spec, i; Bus.control(server, 1) }, Array);
    modSources = modSpecs.collectAs({ arg spec, i;
      var n = Synth.new(spec.defName, [\out, modBusses[i]] ++ spec.args, voiceGroup, \addToHead);
      n.map(\gate, gateBus);
      n;
    }, Array);

    // create osc synths (before mixer), mapping modulators,
    if(osc1Spec.notNil, {
      osc1 = Synth.new(osc1Spec.defName, [\out, voiceBus.index] ++ osc1Spec.args, voiceGroup, \addToTail);
      osc1.performList(\map, osc1Spec.paramMappings);
      osc1.map(\hz, pitchBus, \posMod, modBusses[0]);
    });

    if(osc2Spec.notNil, {
      osc2 = Synth.new(osc2Spec.defName, [\out, voiceBus] ++ osc2Spec.args, voiceGroup, \addToTail);
      osc2.performList(\map, osc2Spec.paramMappings);
      osc2.map(\hz, pitchBus, \posMod, modBusses[0]);
    });

    if(oscSubSpec.notNil, {
      oscSub = Synth.new(oscSubSpec.defName, [\out, voiceBus] ++ oscSubSpec.args, voiceGroup, \addToTail);
      oscSub.performList(\map, oscSubSpec.paramMappings);
      oscSub.map(\hz, pitchBus);
    });

    if(oscNoiseSpec.notNil, {
      oscNoise = Synth.new(oscNoiseSpec.defName, [\out, voiceBus] ++ oscNoiseSpec.args, voiceGroup, \addToTail);
      oscNoise.performList(\map, oscNoiseSpec.paramMappings);
      oscNoise.map(\hz, pitchBus);
    });

    // create filter (after mixer)

    // scale voice ouput by amp env (after filter) and write to main bus
    ampEnv = Synth.new(ampSpec.defName, [\out, ampBus] ++ ampSpec.args, voiceGroup, \addToHead);
    ampEnv.map(\gate, gateBus);
    ampSyn = Synth.new(\lark_vca, [\out, out, \in, voiceBus], voiceGroup, \addToTail);
    ampSyn.map(\ampMod, ampBus, \amp, ampScaleBus);
  }

  start {
    arg hz, amp;
    pitchBus.set(hz);
    ampScaleBus.set(amp);
    gateBus.set(1);

    Post << "   gate: " << gateBus.get << "\n";
    Post << "  pitch: " << hz << "\n";
    Post << "    amp: " << amp << "\n";
  }

  stop {
    gateBus.set(0);
  }

  free {
    voiceGroup.freeAll;
    voiceBus.free;
    pitchBus.free;
    gateBus.free;
    ampBus.free;
    ampScaleBus.free;
    modSources.do({ arg s; s.free; });
    modBusses.do({ arg b; b.free; });
  }

  setPitch { arg hz;
    pitchBus.set(hz);
  }

  setAmp { arg amp;
    ampBus.set(amp);
  }

  printOn {
    arg stream;
    stream << "LarkVoice:\n";
    stream << "-------------------------------\n";
    stream << "  group: " << voiceGroup << "\n";
    stream << "    bus: " << voiceBus << "\n";
    stream << "  pitch: " << pitchBus << "\n";
    stream << "   gate: " << gateBus << "\n";
    stream << " ampMod: " << ampBus << "\n";
    stream << "    amp: " << ampScaleBus << "\n";
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
  var <isReady;

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
    var numWaves = waves.size;

    this.free;    // release any Buffers held by this instance

    numLoaded = 0;

    buffers = Buffer.allocConsecutive(numWaves, server, waves[0].size);
    buffers.do({ arg buf, i;
      buf.loadCollection(waves[i], action: { arg buf;
        numLoaded = numLoaded + 1;
        isReady = numLoaded == numWaves;
        // postln("isReady: " + isReady + "; i: " + i);
      });
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

~f.allocVoices(2)

~f.start(0, 300, 0.5);
~f.stop(0);
~f.voices[0].setPitch(400);
~f.voices[0].ampBus.get
~f.voices[0].voiceBus.scope
~f.voices[0].osc1

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