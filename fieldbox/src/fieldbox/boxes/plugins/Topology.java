package fieldbox.boxes.plugins;

import com.badlogic.jglfw.Glfw;
import field.graphics.FLine;
import field.graphics.Window;
import field.linalg.Vec2;
import field.linalg.Vec4;
import field.utility.Dict;
import field.utility.Rect;
import fieldbox.boxes.Box;
import fieldbox.boxes.Drawing;
import fieldbox.boxes.FLineDrawing;
import fieldbox.boxes.Mouse;

import java.awt.*;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adds: Hold down T to connect elements with other elements.
 * <p>
 * The elements added are TopologyBox, which are subclasses of Box that have a default drawer that draws arcs between
 * endpoints. This is more proof of concept that anything else. The interpretation of this "topology" is completely up
 * to the code in the boxes themselves.
 */
public class Topology extends Box implements Mouse.OnMouseDown {

	static public final Dict.Prop<FunctionOfBox<Collection<Box>>> outward = new Dict.Prop<FunctionOfBox<Collection<Box>>>("outward").doc("a collection of boxes that are the outward connections to this box").toCannon().type();
	static public final Dict.Prop<FunctionOfBox<Collection<Box>>> inward = new Dict.Prop<FunctionOfBox<Collection<Box>>>("inward").doc("a collection of boxes that are the inward connections to this box").toCannon().type();

	private final Box root;
	boolean on = false;

	public Topology(Box root) {
		this.root = root;
		this.properties.putToList(Mouse.onMouseDown, this);

		root.properties.put(outward, (box) -> {
			return box.children().stream().filter(x -> x.properties.has(TopologyBox.head)).filter(x -> x.properties.get(TopologyBox.head).get(root) == box)
				    .map(x -> x.properties.get(TopologyBox.tail).get(root)).collect(Collectors.toSet());
		});
		root.properties.put(inward, (box) -> {
			return box.children().stream().filter(x -> x.properties.has(TopologyBox.tail)).filter(x -> x.properties.get(TopologyBox.tail).get(root) == box)
				    .map(x -> x.properties.get(TopologyBox.tail).get(root)).collect(Collectors.toSet());
		});
	}

	@Override
	public Mouse.Dragger onMouseDown(Window.Event<Window.MouseState> e, int button) {
		if (button == 0) return button0(e);
		return null;
	}

	protected Mouse.Dragger button0(Window.Event<Window.MouseState> e) {
		if (!e.after.keyboardState.keysDown.contains(Glfw.GLFW_KEY_T)) return null;

		Optional<Drawing> drawing = this.find(Drawing.drawing, both()).findFirst();
		Vec2 point = drawing.map(x -> x.windowSystemToDrawingSystem(new Vec2(e.after.x, e.after.y))).orElseThrow(() -> new IllegalArgumentException(" cant mouse around something without drawing support (to provide coordinate system)"));

		Optional<Box> hit = breadthFirst(both()).filter(b -> frame(b) != null).filter(b -> frame(b).intersects(point)).sorted((a, b) -> Float.compare(order(frame(a)), order(frame(b)))).findFirst();

		if (hit.isPresent()) {
			e.properties.put(Window.consumed, true);

			Box origin = hit.get();

			return new Mouse.Dragger() {
				@Override
				public boolean update(Window.Event<Window.MouseState> e, boolean termination) {

					Optional<Drawing> drawing = Topology.this.find(Drawing.drawing, both()).findFirst();
					Vec2 point = drawing.map(x -> x.windowSystemToDrawingSystem(new Vec2(e.after.x, e.after.y))).orElseThrow(() -> new IllegalArgumentException(" cant mouse around something without drawing support (to provide coordinate system)"));

					Optional<Box> hit = breadthFirst(both()).filter(b -> frame(b) != null).filter(b -> frame(b).intersects(point)).sorted((a, b) -> Float.compare(order(frame(a)), order(frame(b)))).findFirst();

					if (hit.isPresent()) {
						showCompleteDrag(origin, hit.get());
						if (termination) {
							completeDrag(origin, hit.get());
						}
					} else {
						showIncompleteDrag(origin, point);
						if (termination) {
							Topology.this.properties.removeFromMap(FLineDrawing.frameDrawing, "__ongoingDrag__");
						}
					}


					return true;
				}


			};
		}
		return null;
	}

	protected void showIncompleteDrag(Box start, Vec2 to) {
		System.out.println(" incomplete drag ");
		this.properties.putToMap(FLineDrawing.frameDrawing, "__ongoingDrag__", (box) -> {

			Rect f1 = frame(start);

			FLine m = TopologyBox.thickenArc(TopologyBox.arc(f1, new Rect(to.x - 10, to.y - 10, 20, 20)), f1, new Rect(to.x-10, to.y-10, 20, 20));

			boolean selected = false;

			float o = -0.5f;

			m.attributes.put(FLineDrawing.fillColor, selected ? new Vec4(1, 1, 1, 1.0f*o) : new Vec4(1, 1, 1, 0.5f*o));
			m.attributes.put(FLineDrawing.strokeColor, selected ? new Vec4(1, 1, 1, 0.25f*o) : new Vec4(1, 1, 1, 0.1f*o));
			m.attributes.put(FLineDrawing.thicken, new BasicStroke(selected ? 3 : 0.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));

			m.attributes.put(FLineDrawing.filled, true);
			m.attributes.put(FLineDrawing.stroked, true);

			m.rect(to.x-10, to.y-10, 20, 20);

			return m;
		});
		Drawing.dirty(this);
	}

	protected float order(Rect r) {
		return Math.abs(r.w) + Math.abs(r.h);
	}

	protected void completeDrag(Box start, Box box) {
		this.properties.removeFromMap(FLineDrawing.frameDrawing, "__ongoingDrag__");

		TopologyBox b = new TopologyBox(start, box);
		start.connect(b);
		box.connect(b);

		Drawing.dirty(this);
	}

	protected void showCompleteDrag(Box start, Box end) {
		this.properties.putToMap(FLineDrawing.frameDrawing, "__ongoingDrag__", (box) -> {

			Rect f1 = frame(start);
			Rect f2 = frame(end);


			FLine m = TopologyBox.thickenArc(TopologyBox.arc(f1, f2), f1, f2);

			boolean selected = true;


			float o = -0.5f;

			m.attributes.put(FLineDrawing.fillColor, selected ? new Vec4(1, 1, 1, 1.0f*o) : new Vec4(1, 1, 1, 0.5f*o));
			m.attributes.put(FLineDrawing.strokeColor, selected ? new Vec4(1, 1, 1, 0.25f*o) : new Vec4(1, 1, 1, 0.1f*o));
			m.attributes.put(FLineDrawing.thicken, new BasicStroke(selected ? 3 : 0.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));

			m.attributes.put(FLineDrawing.filled, true);
			m.attributes.put(FLineDrawing.stroked, true);

			return m;
		});
		Drawing.dirty(this);
	}

	protected Rect frame(Box hitBox) {
		return hitBox.properties.get(frame);
	}

}
