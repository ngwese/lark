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

    fork {
      Post << "Lark.init: sending defs...\n";

      LarkDefs.definitions.do({ arg d;
        postln(" +" + d.name);
        d.send(server);
      });

      Post << "Lark.init: sending tables...\n";

      LarkRegistry.register(\default, LarkWaves.default, server);
      LarkRegistry.register(\random1, LarkWaves.random, server);
      LarkRegistry.register(\random2, LarkWaves.random, server);

      server.sync;

      serverInitialized.test = true;
      serverInitialized.signal;

      Post << "Lark.init: server synced\n";
    };

    // Control busses for global parameters which drive all voices (non per-note parameters)
    globalDefaults = IdentityDictionary.with(
      \osc1_pos -> 0.0,
      \osc1_pos_mod -> 1.0,
      \osc1_level -> 0.dbamp,
      \osc1_tune -> 0.0.midiratio,  // as ratio of pitch, -12.midiratio for -12 semitones

      \osc2_pos -> 0.0,
      \osc2_pos_mod -> 1.0,
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

    fork {
      // wait for defs and default buffers to land
      serverInitialized.wait;

      Post << "Lark.init: setting default configuration...\n";

      // default configuration
      osc1_type = \lark_osc3;
      osc1_enabled = true;
      osc1_table = LarkRegistry.at(\random1);
      osc1_mappings = [
        \level, globalControls[\osc1_level],
        \pos, globalControls[\osc1_pos],
        \posModMult, globalControls[\osc1_pos_mod],
        \hzRatio, globalControls[\osc1_tune],
      ];

      osc2_type = \lark_osc1;
      osc2_enabled = false;
      osc2_table = LarkRegistry.at(\random2);
      osc2_mappings = [
        \level, globalControls[\osc2_level],
        \pos, globalControls[\osc2_pos],
        \posModMult, globalControls[\osc2_pos_mod],
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

      Post << "Lark.init: allocating voices...\n";
      // allocate voices
      this.allocVoices(4);

      Post << "Lark.init: done\n";
    };
  }

  free {
    voices.do({_.free});
    globalControls.values.do({_.free});
    modGroup.free;
    voicesGroup.free;
    fxGroup.free;

    LarkRegistry.free;
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

  oscSpec { arg table, mappings;
    ^LarkSpec.new(\lark_osc1, [
      \i_startBuf, table.baseBuf,
      \i_numBuf, table.numBuf
    ], mappings);
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
  var <modGroup;
  var <filterGroup;
  var <ampGroup;
  var <oscGroup;

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
    modGroup = Group.new(target);
    oscGroup = Group.after(modGroup);
    filterGroup = Group.after(oscGroup);
    ampGroup = Group.after(filterGroup);

    voiceBus = Bus.audio(server, 1);

    // create a voice specific control busses
    pitchBus = Bus.control(server, 1);
    gateBus = Bus.control(server, 1);
    ampBus = Bus.control(server, 1);
    ampScaleBus = Bus.control(server, 1);

    // create secondary envs, mapping gate
    modBusses = modSpecs.collectAs({ arg spec, i; Bus.control(server, 1) }, Array);
    modSources = modSpecs.collectAs({ arg spec, i;
      var n = Synth.new(spec.defName, [\out, modBusses[i]] ++ spec.args, modGroup);
      // n.map(\gate, gateBus);
      n;
    }, Array);

    // create osc synths (before mixer), mapping modulators,
    if(osc1Spec.notNil, {
      osc1 = Synth.new(osc1Spec.defName, [\out, voiceBus.index] ++ osc1Spec.args, oscGroup);
      osc1.performList(\map, osc1Spec.paramMappings);
      osc1.map(\posMod, modBusses[0]);
    });

    if(osc2Spec.notNil, {
      osc2 = Synth.new(osc2Spec.defName, [\out, voiceBus] ++ osc2Spec.args, oscGroup);
      osc2.performList(\map, osc2Spec.paramMappings);
      osc2.map(\posMod, modBusses[0]);

    });

    if(oscSubSpec.notNil, {
      oscSub = Synth.new(oscSubSpec.defName, [\out, voiceBus] ++ oscSubSpec.args, oscGroup);
      oscSub.performList(\map, oscSubSpec.paramMappings);
    });

    if(oscNoiseSpec.notNil, {
      oscNoise = Synth.new(oscNoiseSpec.defName, [\out, voiceBus] ++ oscNoiseSpec.args, oscGroup);
      oscNoise.performList(\map, oscNoiseSpec.paramMappings);
    });

    // create filter (after mixer)

    // scale voice ouput by amp env (after filter) and write to main bus
    ampEnv = Synth.new(ampSpec.defName, [\out, ampBus] ++ ampSpec.args, modGroup);
    ampSyn = Synth.new(\lark_vca, [\out, out, \in, voiceBus], ampGroup);
    ampSyn.map(\ampMod, ampBus, \amp, ampScaleBus);

    // map shared voice controls
    modGroup.map(\gate, gateBus);
    oscGroup.map(\hz, pitchBus);
  }

  osc1_ { arg spec;
    if (osc1.notNil) {
      osc1.free;
    };

    osc1 = Synth.new(spec.defName, [\out, voiceBus.index] ++ spec.args, oscGroup);
    osc1.performList(\map, spec.paramMappings);
    // FIXME: this probably isn't going to pick of the shared voice group mappings
    osc1.map(\hz, pitchBus, \posMod, modBusses[0]);
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
    modGroup.freeAll;
    oscGroup.freeAll;
    filterGroup.freeAll;
    ampGroup.freeAll;
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
    ampScaleBus.set(amp);
  }

  /*
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
  */

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