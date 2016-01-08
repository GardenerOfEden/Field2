package field.graphics;

import field.app.RunLoop;
import field.utility.Dict;
import field.utility.Log;
import field.utility.Util;
import fieldbox.boxes.Box;
import fieldbox.boxes.Drawing;
import fielded.boxbrowser.BoxBrowser;
import fieldnashorn.annotations.HiddenInAutocomplete;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.glFramebufferTexture;

/**
 * FBO - OpenGL Frame Buffer Objects.
 * <p>
 * A Frame Buffer Object is a very general off-screen rendering spot for OpenGL. You can create an FBO from an FBOSpecification (there are helper static methods to help you avoid the mess of historic
 * OpenGL enums, we'll grow these as necessary). They can have multiple layers, optional depth buffers, optional stencils, multisampling, a variety of components and bit-depths and dimensions.
 * <p>
 */
public class FBO extends BaseScene<FBO.State> implements Scene.Perform, OffersUniform<Integer>, BoxBrowser.HasHTMLInformation {


	@Override
	@HiddenInAutocomplete
	public Integer getUniform() {
		try (Util.ExceptionlessAutoCloasable st = GraphicsContext.getContext().stateTracker.save()) {
			return specification.unit;
		}
	}

	int boundCount = 0;

	@Override
	public String generateMarkdown(Box inside, Dict.Prop property) {
		Future<ByteBuffer> m = asyncDebugDownloadRGB8(null);

		// repaint main window, just in case we're there
		RunLoop.main.once(() -> {
			Drawing.dirty(inside);
		});

		String pre
			    = "Framebuffer object has dimensions <b>" + specification.width + "</b>x<b>" + specification.height + "</b> and is bound to texture unit <b>" + specification.unit + "</b><br>";
		if (specification.multisample) pre += "Multisampling is turned on; ";
		if (specification.depth) pre += "There is a depth render-buffer associated with this framebuffer; ";
		if (specification.internalFormat == GL_RGBA32F) pre += "This is a floating-point resolution buffer; ";
		if (specification.layers != 1) pre += "This is a multi-layer (<b>" + specification.layers + "</b> layers) buffer; ";
		pre
			    += "It has drawn its scene <b>" + warnIfZero(drawCount) + "</b> time" + (drawCount == 1 ? "" : "s") + (drawCount == 0 ? "</b>(are you sure .draw() is being called)" : "") + " and it has been bound as a texture <b>" + warnIfZero(boundCount) + "</b> time" + (boundCount == 1 ? "" : "s") + "<br>";

		int tries = 0;
		while (!m.isDone()) {
			try {
				Thread.sleep(100 * tries);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (++tries > 5) return pre + "<b>Image data for FBO is not available (FBO must be actively repainting)<b>";
		}

		try {

			File tmp = File.createTempFile("field", ".jpg");
			new FastJPEG().compress(tmp.getAbsolutePath(), m.get(), specification.width, specification.height);
			byte[] bytes = Files.readAllBytes(tmp.toPath());
			java.util.Base64.Encoder e = java.util.Base64.getEncoder();
			String v = e.encodeToString(bytes);

			String uri = "data:image/jpg;base64," + v;
			String d = "<b>Snapshot of contents &mdash;<br><img style='max-width:300px' src='" + uri + "'/></b>";

			return pre + d;

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return "<b>Image data for FBO is not available (there was an error fetching it)<b>";

	}

	private String warnIfZero(int x) {
		if (x==0) return "<span class='warning'>"+x+"</span>";
		return ""+x;
	}

	@HiddenInAutocomplete
	static public class State extends BaseScene.Modifiable {
		int name = -1;
		int[] text = null;
		int depth = -1;

		int multisample = -1;
		int[] msRenderBuffers = null;
		int msDepth = -1;
	}

	static public class FBOSpecification {
		public final int unit;
		public final int internalFormat;
		public final int width;
		public final int height;
		public final int format;
		public final int type;
		public final int elementSize;
		public final boolean depth;
		public final int num;
		public final int layers;
		public final boolean multisample;

		public FBOSpecification(int unit, int internalFormat, int width, int height, int format, int type, int elementSize, boolean depth, int num, boolean multisample, int layers) {
			this.unit = unit;
			this.internalFormat = internalFormat;
			this.width = width;
			this.height = height;
			this.format = format;
			this.type = type;
			this.elementSize = elementSize;

			this.depth = depth;
			this.num = num;
			this.multisample = multisample;

			this.layers = layers;
		}

		static public FBOSpecification singleFloat(int unit, int width, int height) {
			return new FBOSpecification(unit, GL_RGBA32F, width, height, GL_RGBA, GL_FLOAT, 32, false, 1, false, 1);
		}

		static public FBOSpecification layeredFloat(int unit, int width, int height, int layers) {
			return new FBOSpecification(unit, GL_RGBA32F, width, height, GL_RGBA, GL_FLOAT, 32, false, 1, false, layers);
		}

		static public FBOSpecification rgba(int unit, int width, int height) {
			return new FBOSpecification(unit, GL_RGBA, width, height, GL_RGBA, GL_BYTE, 8, false, 1, false, 1);
		}

		static public FBOSpecification rgbaMultisample(int unit, int width, int height) {
			return new FBOSpecification(unit, GL_RGBA, width, height, GL_RGBA, GL_BYTE, 8, false, 1, true, 1);
		}

		@Override
		public String toString() {
			return "FBOSpecification{" +
				    "unit=" + unit +
				    ", internalFormat=" + internalFormat +
				    ", width=" + width +
				    ", height=" + height +
				    ", format=" + format +
				    ", type=" + type +
				    ", elementSize=" + elementSize +
				    ", depth=" + depth +
				    ", num=" + num +
				    ", layers=" + layers +
				    ", multisample=" + multisample +
				    '}';
		}
	}

	public final FBOSpecification specification;

	public Scene scene = new Scene();

	public FBO(FBOSpecification specification) {
		this.specification = specification;
	}


	protected State setup() {
		Log.log("graphics.trace", () -> "setting up FBO " + specification);

		GraphicsContext.checkError();

		State s = new State();
		s.name = glGenFramebuffers();

		if (specification.multisample) {

			s.multisample = glGenFramebuffers();

			s.msRenderBuffers = new int[specification.num];

			glBindFramebuffer(GL_FRAMEBUFFER, s.multisample);

			for (int i = 0; i < s.msRenderBuffers.length; i++) {
				s.msRenderBuffers[i] = glGenRenderbuffers();
				int converageSamples = 4;


				glBindRenderbuffer(GL_RENDERBUFFER, s.msRenderBuffers[i]);

				glRenderbufferStorageMultisample(GL_RENDERBUFFER, converageSamples, specification.internalFormat, specification.width, specification.height);

				glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_RENDERBUFFER, s.msRenderBuffers[i]);

			}

			if (specification.depth) {
				s.msDepth = glGenRenderbuffers();
				int depthSamples = 4;

				glBindRenderbuffer(GL_RENDERBUFFER, s.msDepth);

				glRenderbufferStorageMultisample(GL_RENDERBUFFER, depthSamples, GL_DEPTH24_STENCIL8, specification.width, specification.height);

				glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, s.msDepth);

			}


			int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);

			if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalArgumentException(" bad status, " + status);
		}

		GraphicsContext.checkError();

		s.text = new int[specification.num];
		for (int i = 0; i < s.text.length; i++)
			s.text[i] = glGenTextures();
		if (specification.depth) s.depth = glGenRenderbuffers();


		glBindFramebuffer(GL_FRAMEBUFFER, s.name);
		GraphicsContext.checkError();


		if (specification.layers == 1) {
			for (int i = 0; i < s.text.length; i++) {

				glBindTexture(GL_TEXTURE_2D, s.text[i]);

				glTexImage2D(GL_TEXTURE_2D, 0, specification.internalFormat, specification.width, specification.height, 0, specification.format, specification.type, (ByteBuffer) null);

				glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, s.text[i], 0);

				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);

				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

			}
		} else {
			for (int i = 0; i < s.text.length; i++) {

				glBindTexture(GL_TEXTURE_2D_ARRAY, s.text[i]);

				glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, specification.internalFormat, specification.width, specification.height, specification.layers, 0, specification.format,
					     specification.type, (ByteBuffer) null);

				glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, s.text[i], 0);

				glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

				glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

				glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);

				glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

			}

		}
		if (specification.depth) {

			glBindRenderbuffer(GL_RENDERBUFFER, s.depth);
			glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, specification.width, specification.height);
			glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, s.depth);

		}

		for (int i = 0; i < s.text.length; i++) {

			glDrawBuffer(GL_COLOR_ATTACHMENT0 + i);
			glClearColor(0, 0, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		}

		int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalArgumentException(" bad status, " + status);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		GraphicsContext.checkError();

		Log.log("graphics.trace", () -> "finished setting up FBO " + specification + " status is " + status);

		return s;
	}

	AtomicReference<Runnable> postDrawQueue = new AtomicReference<>();

	int drawCount = 0;

	public boolean draw() {
		drawCount++;
		GraphicsContext.checkError(() -> "on FBO draw entry, specification " + specification);
		try (Util.ExceptionlessAutoCloasable st = GraphicsContext.getContext().stateTracker.save()) {

			State s = GraphicsContext.get(this, this::setup);

			GraphicsContext.getContext().stateTracker.fbo.set(specification.multisample ? s.multisample : s.name);

			int[] v = {0, 0, specification.width, specification.height};

			GraphicsContext.getContext().stateTracker.scissor.set(v);

			GraphicsContext.getContext().stateTracker.viewport.set(v);


			scene.updateAll();

			if (specification.multisample) {
				glBindFramebuffer(GL_READ_FRAMEBUFFER, s.multisample);
				glBindFramebuffer(GL_DRAW_FRAMEBUFFER, s.name);
				for (int i = 0; i < s.text.length; i++) {
					glDrawBuffer(GL_COLOR_ATTACHMENT0 + i);
					glReadBuffer(GL_COLOR_ATTACHMENT0 + i);
					glBlitFramebuffer(0, 0, specification.width, specification.height, 0, 0, specification.width, specification.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
				}
				glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
				glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
			}


			Runnable m = postDrawQueue.getAndSet(null);

			if (m != null) m.run();

			GraphicsContext.checkError(() -> "on FBO draw exit1");

			return true;
		} finally {
			GraphicsContext.checkError(() -> "on FBO draw exit2");
		}
	}


	public void setScene(Scene d) {
		this.scene = d;
	}

	@HiddenInAutocomplete
	public int getOpenGLFrameBufferNameInCurrentContext() {
		State s = GraphicsContext.get(this, this::setup);
		if (s == null) {
			throw new NullPointerException("FBO not initialized in this context");
		}
		return s.name;
	}

	@HiddenInAutocomplete
	public int getOpenGLTextureNameInCurrentContext() {
		State s = GraphicsContext.get(this, this::setup);
		if (s == null) {
			throw new NullPointerException("FBO not initialized in this context");
		}
		return s.text[0];
	}

	@Override
	protected boolean perform0() {
		boundCount++;

		Log.log("graphics.trace", () -> "binding FBO to texture unit " + specification.unit);
		State s = GraphicsContext.get(this);
		for (int i = 0; i < s.text.length; i++) {
			if (specification.layers == 1) {
				glActiveTexture(GL_TEXTURE0 + specification.unit + i);
				glBindTexture(GL_TEXTURE_2D, s.text[i]);
			} else {
				glActiveTexture(GL_TEXTURE0 + specification.unit + i);
				glBindTexture(GL_TEXTURE_2D_ARRAY, s.text[i]);
			}
		}
		return true;
	}

	@Override
	public int[] getPasses() {
		return new int[]{-1, 1};
	}

	public void deallocate(State s) {
		glDeleteFramebuffers(s.name);
		if (s.multisample != -1) glDeleteFramebuffers(s.multisample);
		glDeleteFramebuffers(s.name);
		if (s.text != null) for (int i = 0; i < s.text.length; i++)
			glDeleteTextures(s.text[i]);
		if (s.depth != -1) glDeleteRenderbuffers(s.depth);
		if (s.msDepth != -1) glDeleteRenderbuffers(s.msDepth);
		if (s.msRenderBuffers != null) for (int i = 0; i < s.msRenderBuffers.length; i++)
			glDeleteRenderbuffers(s.msRenderBuffers[i]);
	}

	/**
	 * this operates asynchronously if we are not currently inside the draw loop (that is, if there is no valid context for our thread). only one debug download can be pending at a time. You can
	 * pass in null to this routine and you'll get a correctly sized ByteByffer back that you can reuse for subsequent calls. Regardless of the original format of the FBO this always returns
	 * something that's RGBA / byte,.
	 */
	public Future<ByteBuffer> asyncDebugDownloadRGBA8(ByteBuffer to) {
		ByteBuffer ato;

		int sz = specification.width * specification.height * 4;
		if (to == null || !to.isDirect() || to.limit() < sz) {
			ato = ByteBuffer.allocateDirect(sz);
		} else ato = to;

		CompletableFuture<ByteBuffer> c = new CompletableFuture<ByteBuffer>() {
			@Override
			public ByteBuffer get() throws InterruptedException, ExecutionException {
				if (!isDone()) return ato;
				return super.get();
			}
		};
		Runnable r = () -> {
			int[] a = {0};
			a[0] = glGetInteger(GL_FRAMEBUFFER_BINDING);
			glBindFramebuffer(GL_FRAMEBUFFER, getOpenGLFrameBufferNameInCurrentContext());
			glReadPixels(0, 0, specification.width, specification.height, GL11.GL_RGBA, GL_UNSIGNED_BYTE, ato);
			glBindFramebuffer(GL_FRAMEBUFFER, a[0]);
			c.complete(ato);
		};

		if (GraphicsContext.getContext() == null) {
			postDrawQueue.set(r);
		} else {
			r.run();
		}

		return c;
	}

	/**
	 * this operates asynchronously if we are not currently inside the draw loop (that is, if there is no valid context for our thread). only one debug download can be pending at a time. You can
	 * pass in null to this routine and you'll get a correctly sized ByteByffer back that you can reuse for subsequent calls. Regardless of the original format of the FBO this always returns
	 * something that's RGB / byte,.
	 */
	public Future<ByteBuffer> asyncDebugDownloadRGB8(ByteBuffer to) {
		ByteBuffer ato;

		int sz = specification.width * specification.height * 4;
		if (to == null || !to.isDirect() || to.limit() < sz) {
			ato = ByteBuffer.allocateDirect(sz);
		} else ato = to;

		CompletableFuture<ByteBuffer> c = new CompletableFuture<ByteBuffer>() {
			@Override
			public ByteBuffer get() throws InterruptedException, ExecutionException {
				if (!isDone()) return ato;
				return super.get();
			}
		};
		Runnable r = () -> {
			int[] a = {0};
			a[0] = glGetInteger(GL_FRAMEBUFFER_BINDING);
			glBindFramebuffer(GL_FRAMEBUFFER, getOpenGLFrameBufferNameInCurrentContext());
			glReadPixels(0, 0, specification.width, specification.height, GL11.GL_RGB, GL_UNSIGNED_BYTE, ato);
			glBindFramebuffer(GL_FRAMEBUFFER, a[0]);
			c.complete(ato);
		};

		if (GraphicsContext.getContext() == null) {
			postDrawQueue.set(r);
		} else {
			r.run();
		}

		return c;
	}

	/**
	 * this operates asynchronously if we are not currently inside the draw loop (that is, if there is no valid context for our thread). only one debug download can be pending at a time. You can
	 * pass in null to this routine and you'll get a correctly sized ByteByffer back that you can reuse for subsequent calls. Regardless of the original format of the FBO this always returns
	 * something that's RGBA / float,.
	 */
	public Future<FloatBuffer> asyncDebugDownloadRGBAFloat(FloatBuffer to) {
		FloatBuffer ato;

		int sz = specification.width * specification.height * 4 * 4;
		if (to == null || !to.isDirect() || to.limit() < sz) {
			ato = ByteBuffer.allocateDirect(sz)
					.order(ByteOrder.nativeOrder())
					.asFloatBuffer();
		} else ato = to;

		// we hack this so that people don't hang the main thread (requiring a restart of Field) by blindly calling 'get' when isDone is false
		CompletableFuture<FloatBuffer> c = new CompletableFuture<FloatBuffer>() {
			@Override
			public FloatBuffer get() throws InterruptedException, ExecutionException {
				if (!isDone()) return ato;
				return super.get();
			}
		};

		Runnable r = () -> {
			int[] a = {0};
			a[0] = glGetInteger(GL_FRAMEBUFFER_BINDING);
			glBindFramebuffer(GL_FRAMEBUFFER, getOpenGLFrameBufferNameInCurrentContext());
			glReadPixels(0, 0, specification.width, specification.height, GL11.GL_RGBA, GL_FLOAT, ato);
			glBindFramebuffer(GL_FRAMEBUFFER, a[0]);
			c.complete(ato);
		};

		if (GraphicsContext.getContext() == null) {
			postDrawQueue.set(r);
		} else {
			r.run();
		}

		return c;
	}

}
