~p = "/Applications/WaveEdit/banks/ROM B.wav";
~b = Buffer.read(s, "/Applications/WaveEdit/banks/ROM B.wav");
~b.query;


~f = SoundFile.openRead(~p);
~x = Signal.newClear(~f.numFrames);
~f.readData(~x);
~x.size
mod(~x.size, 64)

~x[0..255].plot
~x.slice(0, 255).plot
~x.plot

~w = LarkWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256);

~w.size

~d = File.readAllString("~/wt.json".standardizePath);
~d.parseJSON;

~z = "~/wt.json".standardizePath.parseJSONFile;
~z["tables"].size


(
MIDIClient.init;
MIDIIn.connectAll;

~engine = Lark.new(s);
~engine.osc1_type = \lark_osc3;
// ~engine.osc1_table = LarkTable.fromFile(
//   s,
//   "/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav",
//   2048
// );

~engine.osc2_type = \lark_osc1;
~engine.osc2_table = LarkTable.fromFile(
  s,
  // "/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/Jno.wav", 2048
  "/Applications/WaveEdit/banks/ROM B.wav", 256
);

~notes = Array.newClear(128);

MIDIdef.noteOn(\noteOn, { arg vel, num, chan, src;
  ~notes[num] = ~engine.noteOn(num.midicps, vel * 0.00315);
});

MIDIdef.noteOff(\noteOff, { arg vel, num, chan, src;
  ~notes[num].stop;
  ~notes[num].release;
});

)

~engine.noise_enabled = false;
~engine.setControl(\noise_level, 0.02);

~engine.sub_enabled = false;
~engine.sub_enabled = true;
~engine.setControl(\sub_tune, -5.midiratio);

~engine.osc1_table
~engine.osc1_type = \lark_osc3

~engine.osc2Spec
~engine.posSpec
~engine.osc1_table = ~engine.defaultTable
~engine.globalControls.asPairs


~v = LarkVoice.new
~x = ~v.start(~engine.server, ~engine.voicesGroup, 0, ~engine.osc1Spec, ~engine.ampSpec, [~engine.posSpec]);
~x.modBusses
~x.modSources

~x.gateBus.get
~x.pitchBus.get
~x.ampBus.get
~x.stop
~x.gateBus.set(1);
~x.pitchBus.set(80);
~x.ampBus.set(0.1);

~engine.buffers[26]
~engine.osc1_type = \lark_silent;

(
~engine.osc1_table = LarkTable.fromFile(
  s,
  "/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav",
  2048
);
)

~engine.osc1_table = ~engine.defaultTable

~engine.defaultTable.buffers
~engine.osc1_table.buffers

if(true, { 1 });

(
~f = { arg one, two; Post << "f: " << one << ", " << two << "\n"; };
)
~f.postcs

~f.applyTo([\one, 2, \two, 4])


NetAddr.langPort

h = CroneHarness.new;
h.engine('Lark');

h.cmd('/start', [0, 300, 0.5]);
h.cmd('/stop', [0]);

Crone.reportEngines;


4.collectAs({arg i; i}, Array).size;

Crone.engine.lark.voices[0]
Crone.engine.lark.voices[0].ampEnv.get(\gate, {arg v; v.postln; });
Crone.engine.lark.globalControls[\osc1_pos].get
Crone.engine.lark.voices[0].voiceBus.scope
