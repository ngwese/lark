Engine_Lark : CroneEngine {
  var <lark;
  var <voices;

  *new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

  alloc {
    lark = Lark.new(this.context.server, this.context.xg, this.context.out_b);

    context.server.sync;

    this.addCommand(\start, "iff", { arg msg;
      var n = msg[1];

      // if re-using id of an active voice, kill the voice
      // TODO: should this fade the voice out?
      if (lark.voiceStarted[n], {
        Post << "Stopping voice [" << n << "]\n";
        lark.stop(n);
      });

      Post << "voice " << n << " start(" << msg[2] << ", " << msg[3] << ")\n";

      lark.start(n, msg[2], msg[3]);
    });

    this.addCommand(\stop, "i", { arg msg;
      lark.stop(msg[1]);
    });

    this.addCommand(\set_control, "sf", { arg msg;
      // MAINT: temporary stop gap until commands are defined for all messages
      var control = msg[1].asSymbol;
      lark.setControl(control, msg[2]);
    });
  }

  free {
    lark.free;
  }
}