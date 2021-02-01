LarkDefs {
  classvar <oscillators;
  classvar <subOscillators;
  classvar <modulators;
  classvar <noises;
  classvar <amplifiers;
  classvar <filters;

  *initClass {

    //
    // Modulators
    //

    modulators = List.new;

    modulators.add(
      SynthDef(\lark_adsr, {
        arg out=0, gate=0, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1, i_done=0;
        var sig;

        sig = EnvGen.kr(
          Env.adsr(i_atk, i_decay, i_sus, i_rel),
          gate: gate,
        );

        ReplaceOut.kr(out, sig);
      })
    );

    modulators.add(
      SynthDef(\lark_sweep, {
        arg out=0, gate=0, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1, i_done=0;
        var sig;

        sig = EnvGen.kr(
          Env([0,1,0.5,0],[i_atk,i_sus,i_rel],[1,0,-1]),
          gate: gate,
        );

        ReplaceOut.kr(out, sig);
      })
    );

    modulators.add(
      SynthDef(\lark_noise_sweep, {
        arg out=0;
        var sig;

        sig = LFNoise1.kr(2).unipolar;

        ReplaceOut.kr(out, Clip.kr(sig));
      })
    );

    //
    // Oscillator
    //

    oscillators = List.new;

    oscillators.add(
      SynthDef(\lark_sine, {
        arg out=0, hz=300, hzRatio=1.0, hzMod=0, hzModMult=1.0, level=1.0;
        var sig, pitch;

        pitch = (hz * hzRatio) + (hzMod * hzModMult);
        sig = SinOsc.ar(hz, mul: 0.3);

        Out.ar(out, sig * level);
      })
    );

    oscillators.add(
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

    oscillators.add(
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

    //
    // Sub Oscillators
    //

    subOscillators = List.new;

    subOscillators.add(
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

    //
    // Noise Oscillators
    //

    noises = List.new;

    noises.add(
      SynthDef(\lark_white_noise, {
        arg out=0, level=1.0;
        Out.ar(out, WhiteNoise.ar(level));
      })
    );


    //
    // Amplifiers
    //

    amplifiers = List.new;

    amplifiers.add(
      SynthDef(\lark_vca, {
        arg out=0, in=0, amp=0.2, ampMod=1;
        var sig = In.ar(in) * ampMod * amp;
        Out.ar(out, sig!2);
      })
    );


    //
    // Filters
    //

    filters = List.new;
  }

  *definitions {
    ^modulators ++ oscillators ++ subOscillators ++ noises ++ amplifiers ++ filters;
  }

  *collectNames { arg defs;
    ^defs.collectAs({arg d; d.name}, List);
  }

  *oscNames {
    ^this.collectNames(oscillators);
  }

  *subOscNames {
    ^this.collectNames(subOscillators);
  }

  *noiseNames {
    ^this.collectNames(noises);
  }

  *ampNames {
    ^this.collectNames(amplifiers);
  }

  *filterNames {
    ^this.collectNames(filters);
  }
}

/*

LarkDefs.filterNames
LarkDefs.oscNames
*/