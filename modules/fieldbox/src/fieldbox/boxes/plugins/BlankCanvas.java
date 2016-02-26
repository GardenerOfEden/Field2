package fieldbox.boxes.plugins;

import field.graphics.FLine;
import field.linalg.Vec4;
import field.utility.Cached;
import field.utility.Pair;
import field.utility.Rect;
import fieldagent.Main;
import fieldbox.boxes.Box;
import fieldbox.boxes.Drawing;

import java.util.Collections;

import static fieldbox.boxes.FLineDrawing.frameDrawing;
import static field.graphics.StandardFLineDrawing.*;

/**
 * When there's nothing in the canvas, prompt users to create something
 */
public class BlankCanvas extends Box {

	public BlankCanvas(Box root) {

		this.properties.putToMap(frameDrawing, "__textprompt__", new Cached<Box, Object, FLine>((box, previously) -> {

			// 1 is that timeslider

			if (root.children().stream().filter(x -> x.properties.has(Box.frame)).count()>1) return new FLine();


			Rect m = this.find(Drawing.drawing, this.both()).findFirst().map(x -> x.getCurrentViewBounds(this)).orElseGet(() -> new Rect(0,0,100,100));

			FLine f = new FLine();

			f.attributes.put(hasText, true);
			f.moveTo(m.x + m.w / 2, m.y + m.h / 2 - 14);

			String text = "Press N to create new box";

			f.nodes.get(f.nodes.size()-1).attributes.put(textSpans, Collections.singletonList(text));
			f.nodes.get(f.nodes.size()-1).attributes.put(textColorSpans, Collections.singletonList(new Vec4(1,1,1,0.1f)));
			f.nodes.get(f.nodes.size()-1).attributes.put(textScale, 3.5f);

			return f;

		}, box -> new Pair<>(root.children().size(), this.find(Drawing.drawing, this.both()).findFirst().map(x -> x.getCurrentViewBounds(this)).orElseGet(() -> new Rect(0,0,100,100)))));

		this.properties.putToMap(frameDrawing, "__textprompt2__", new Cached<Box, Object, FLine>((box, previously) -> {

			// 1 is that timeslider
			if (root.children().stream().filter(x -> x.properties.has(Box.frame)).count()>1) return new FLine();

			Rect m = this.find(Drawing.drawing, this.both()).findFirst().map(x -> x.getCurrentViewBounds(this)).orElseGet(() -> new Rect(0,0,100,100));

			FLine f = new FLine();

			f.attributes.put(hasText, true);
			f.moveTo(m.x + m.w / 2, m.y + m.h / 2 + 14);

			String text = "Right-click "+ (Main.os==Main.OS.mac ? "/ ctrl-drag " : "") + "for menus";

			f.nodes.get(f.nodes.size()-1).attributes.put(textSpans, Collections.singletonList(text));
			f.nodes.get(f.nodes.size()-1).attributes.put(textColorSpans, Collections.singletonList(new Vec4(1,1,1,0.1f)));
			f.nodes.get(f.nodes.size()-1).attributes.put(textScale, 1.5f);

			return f;

		}, box -> new Pair<>(root.children().size(), this.find(Drawing.drawing, this.both()).findFirst().map(x -> x.getCurrentViewBounds(this)).orElseGet(() -> new Rect(0,0,100,100)))));


	}
}
