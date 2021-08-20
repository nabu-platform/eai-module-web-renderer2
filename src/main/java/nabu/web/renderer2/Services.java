package nabu.web.renderer2;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import com.github.kklisura.cdt.launch.ChromeArguments;
import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.protocol.commands.IO;
import com.github.kklisura.cdt.protocol.commands.Network;
import com.github.kklisura.cdt.protocol.commands.Page;
import com.github.kklisura.cdt.protocol.commands.Runtime;
import com.github.kklisura.cdt.protocol.commands.Tracing;
import com.github.kklisura.cdt.protocol.events.console.MessageAdded;
import com.github.kklisura.cdt.protocol.events.log.EntryAdded;
import com.github.kklisura.cdt.protocol.events.network.LoadingFinished;
import com.github.kklisura.cdt.protocol.events.network.RequestIntercepted;
import com.github.kklisura.cdt.protocol.events.network.RequestWillBeSent;
import com.github.kklisura.cdt.protocol.events.page.LifecycleEvent;
import com.github.kklisura.cdt.protocol.events.page.LoadEventFired;
import com.github.kklisura.cdt.protocol.support.types.EventHandler;
import com.github.kklisura.cdt.protocol.types.io.Read;
import com.github.kklisura.cdt.protocol.types.page.PrintToPDF;
import com.github.kklisura.cdt.protocol.types.page.PrintToPDFTransferMode;
import com.github.kklisura.cdt.protocol.types.runtime.Evaluate;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;

import be.nabu.utils.mime.api.Part;
//import javafx.beans.value.ChangeListener;
//import javafx.beans.value.ObservableValue;
//import javafx.concurrent.Worker.State;
//import javafx.scene.web.WebView;
//import javafx.application.Application;
//import javafx.stage.Stage;
//import javafx.scene.web.WebEngine;
import nabu.web.renderer2.types.ExecuteResult;
import nabu.web.renderer2.types.ExecuteResult.LifeCycleResult;

/**
 * TODO:
 * Need a global timeout: some pages don't end up in the correct lifecycle states (die early?), the service will simply run forever...
 */
@WebService
public class Services {
		
//	private static Thread javaFxThread;
//
//	public static class AsNonApp extends Application {
//        @Override
//        public void start(Stage primaryStage) throws Exception {
//            // no-op
//        }
//    }
	
//	private void ensureThread() {
//		if (javaFxThread == null) {
//			javaFxThread = new Thread("JavaFX Init Thread") {
//		        public void run() {
//		            Application.launch(AsNonApp.class, new String[0]);
//		        }
//		    };
//		    javaFxThread.setDaemon(true);
//		    javaFxThread.start();
//		}
//	}
	
	// 3 modes: check if page builder application -> use frontend setting to determine when rendering is done?
	// if not page builder, either we go for a certain target state and/or a timeout to be triggered at which point we send back whatever is there?
	// allow a random script to be evaluated to determine load is done?
	
	// TODO: return timing values as well
	// than we can do some performance testing as well!
	public ExecuteResult execute(@WebParam(name = "method") String method, 
			@NotNull @WebParam(name = "url") URI url, @WebParam(name = "part") Part part, @WebParam(name = "javascript") String javascript,
			@WebParam(name = "timeout") Long timeout, @WebParam(name = "timeoutUnit") TimeUnit timeUnit,
			@WebParam(name = "asPdf") Boolean asPdf) {
		
		try {
			// not sure if this will work when doing stuff like websockets or frequent polling or...
			boolean waitForNetworkIdle = true;
			
			// Create chrome launcher.
		    final ChromeLauncher launcher = new ChromeLauncher();
		    
		    CountDownLatch latch = new CountDownLatch(1);
		    
		    ChromeArguments arguments = ChromeArguments.builder()
		    		.headless(true)
		    		.additionalArguments("user-agent", "Nabu-Renderer/1.1")
		    		.additionalArguments("no-sandbox", true)
		    		.build();
		    // Launch chrome either as headless (true) or regular (false).
	//	    ChromeService chromeService = launcher.launch(true);
		    ChromeService chromeService = launcher.launch(arguments);
		    
		    // Create empty tab ie about:blank.
		    ChromeTab tab = chromeService.createTab();
		    
			// Get DevTools service to this tab
		    ChromeDevToolsService devToolsService = chromeService.createDevToolsService(tab);
		    
		    devToolsService.getConsole().enable();
		    devToolsService.getConsole().onMessageAdded(new EventHandler<MessageAdded>() {
				@Override
				public void onEvent(MessageAdded arg0) {
					System.out.println("> " + arg0.getMessage().getText());
				}
		    });
		    
		    devToolsService.getLog().enable();
		    devToolsService.getLog().onEntryAdded(new EventHandler<EntryAdded>() {
				@Override
				public void onEvent(EntryAdded arg0) {
					System.out.println(">> " + arg0.getEntry().getText());
				}
		    });
		    
			// Get individual commands
			Page page = devToolsService.getPage();
			Network network = devToolsService.getNetwork();
			
			Tracing tracing = devToolsService.getTracing();
			tracing.onDataCollected(
			        event -> {
			          if (event.getValue() != null) {
			            System.out.println("----------------------------> trace: " + event.getValue());
			          }
			        });
			tracing.start();
			
			if (javascript != null) {
	//			page.addScriptToEvaluateOnLoad(javascript);
				page.addScriptToEvaluateOnNewDocument(javascript);
			}
			// Log requests with onRequestWillBeSent event handler.
			network.onRequestWillBeSent(new EventHandler<RequestWillBeSent>() {
				@Override
				public void onEvent(RequestWillBeSent event) {
					System.out.printf(
							"request: %s %s%s",
							event.getRequest().getMethod(),
							event.getRequest().getUrl(),
							System.lineSeparator());
				}
			});
	
	//		network.onLoadingFinished(new EventHandler<LoadingFinished>() {
	//			@Override
	//			public void onEvent(LoadingFinished arg0) {
	//				// get html content
	//				Runtime runtime = devToolsService.getRuntime();
	//				Evaluate evaluation = runtime.evaluate("document.documentElement.outerHTML");
	//				result.add((String) evaluation.getResult().getValue());
	//				// Close the tab and close the browser when loading finishes.
	//				chromeService.closeTab(tab);
	//				launcher.close();
	//			}
	//		});
			
			network.onRequestIntercepted(new EventHandler<RequestIntercepted>() {
				@Override
				public void onEvent(RequestIntercepted event) {
					String interceptionId = event.getInterceptionId();
					Map<String, Object> headers = new HashMap<String, Object>();
				}
			});
			
			ExecuteResult result = new ExecuteResult();
			
			page.setLifecycleEventsEnabled(true);
			List<Boolean> state = new ArrayList<Boolean>();
			
			
//			String targetState = "firstMeaningfulPaint";
			String targetState = "firstContentfulPaint";
	//		String targetState = "firstPaint";
			/*
			 	----------> lifecycle: init
				----------> lifecycle: DOMContentLoaded
				----------> lifecycle: load
				----------> lifecycle: firstPaint
				----------> lifecycle: firstContentfulPaint
				----------> lifecycle: firstImagePaint
				----------> lifecycle: firstMeaningfulPaintCandidate
				----------> lifecycle: networkAlmostIdle
				----------> lifecycle: firstMeaningfulPaint
				----------> lifecycle: networkIdle
			 */
			Date started = new Date();
			page.onLifecycleEvent(new EventHandler<LifecycleEvent>() {
				@Override
				public void onEvent(LifecycleEvent arg0) {
					try {
						System.out.println("--------------> lifecycle: " + arg0.getName());
						LifeCycleResult lifeCycle = new LifeCycleResult();
						lifeCycle.setName(arg0.getName());
						lifeCycle.setTime(new Date().getTime() - started.getTime());
						result.getLifeCycle().add(lifeCycle);
						
//						boolean stop = false;
//						// we wait for the meaningfulcontent
//						if (targetState.equals(arg0.getName())) {
//							if (waitForNetworkIdle) {
//								state.add(true);
//							}
//							else {
//								stop = true;
//							}
//						}
//						else if (waitForNetworkIdle && "networkIdle".equals(arg0.getName()) && !state.isEmpty()) {
//							stop = true;
//						}
//						if (stop) {
//	//						try {
//	//							Thread.sleep(5000);
//	//						} catch (InterruptedException e) {
//	//							// TODO Auto-generated catch block
//	//							e.printStackTrace();
//	//						}
//							try {
//								Runtime runtime = devToolsService.getRuntime();
//								// we explicitly try to clear all the templates, this is relevant for our own applications, less so for others
//								try {
//									runtime.evaluate("window.clearTemplates()");
//								}
//								catch (Exception e) {
//									// doesn't matter if it failed...
//									e.printStackTrace();
//								}
//								Evaluate evaluation = runtime.evaluate("document.documentElement.outerHTML");
//								result.setContent((String) evaluation.getResult().getValue());
//								// Close the tab and close the browser when loading finishes.
//								chromeService.closeTab(tab);
//								devToolsService.close();
//								launcher.close();
//							}
//							finally {
//								latch.countDown();
//							}
//						}
					}
					catch (Throwable e) {
						e.printStackTrace();
					}
				}
			});
			
			// we check if it is page builder
			page.onLoadEventFired(new EventHandler<LoadEventFired>() {
				@Override
				public void onEvent(LoadEventFired arg0) {
					Runtime runtime = devToolsService.getRuntime();
					try {
						// we need the property "stable" in the page builder to make sure we have the latest version
						Evaluate evaluation = runtime.evaluate("application && application.services && application.services.page && application.services.page.hasOwnProperty(\"stable\")");
						// if we have an object, it is page builder
						result.setPageBuilder(Boolean.TRUE.equals(evaluation.getResult().getValue()));
					}
					catch (Exception e) {
						// doesn't matter if it failed...
						e.printStackTrace();
					}
				}
			});
			
	//		page.onLoadEventFired(new EventHandler<LoadEventFired>() {
	//			@Override
	//			public void onEvent(LoadEventFired arg0) {
	//				// get html content
	//				Runtime runtime = devToolsService.getRuntime();
	//				// manually trigger the load event?
	//				Evaluate load = runtime.evaluate("window.dispatchEvent(new Event('load'))");
	//				Evaluate evaluation = runtime.evaluate("document.documentElement.outerHTML");
	//				result.add((String) evaluation.getResult().getValue());
	//				// Close the tab and close the browser when loading finishes.
	//				chromeService.closeTab(tab);
	//				launcher.close();
	//			}
	//		});
	
			// TODO: inject server side rendering headers?
			
			// Enable network events.
			network.enable();
			
//			page.onLoadEventFired(new EventHandler<LoadEventFired>() {
//				@Override
//				public void onEvent(LoadEventFired arg0) {
//					System.out.println("**** 2 Closing dev tools on load");
//					devToolsService.close();
//				}
//			});
			
			// enable page events
			page.enable();
			
			page.navigate(url.toString());
			
//			devToolsService.waitUntilClosed();
			
			if (timeout == null) {
				timeout = 30l;
				timeUnit = TimeUnit.SECONDS;
			}
			else if (timeUnit == null) {
				timeUnit = TimeUnit.MILLISECONDS;
			}
			
//			latch.await(timeout, timeUnit == null ? TimeUnit.MILLISECONDS : timeUnit);
			
			Runtime runtime = devToolsService.getRuntime();
			waitForStable(page, runtime, TimeUnit.MILLISECONDS.convert(timeout, timeUnit), result);
			
			if (asPdf != null && asPdf) {
				Boolean landscape = false;
				Boolean displayHeaderFooter = false;
				Boolean printBackground = true;
				Double scale = 1d;
				Double paperWidth = 8.27d; // A4 paper format
				Double paperHeight = 11.7d; // A4 paper format
				Double marginTop = 0d;
				Double marginBottom = 0d;
				Double marginLeft = 0d;
				Double marginRight = 0d;
				String pageRanges = "";
				Boolean ignoreInvalidPageRanges = false;
				String headerTemplate = "";
				String footerTemplate = "";
				Boolean preferCSSPageSize = false;
				PrintToPDFTransferMode mode = PrintToPDFTransferMode.RETURN_AS_STREAM;
				PrintToPDF printToPDF = page.printToPDF(landscape,
					displayHeaderFooter,
					printBackground,
					scale,
					paperWidth,
					paperHeight,
					marginTop,
					marginBottom,
					marginLeft,
					marginRight,
					pageRanges,
					ignoreInvalidPageRanges,
					headerTemplate,
					footerTemplate,
					preferCSSPageSize,
					mode);
				IO io = devToolsService.getIO();
				int offset = 0;
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				try {
					int READ_BUFFER_SIZE = 1048576;
					do {
						final Read read = io.read(printToPDF.getStream(), offset, READ_BUFFER_SIZE);
						if (read.getBase64Encoded() == Boolean.TRUE) {
							byte[] decode = Base64.getDecoder().decode(read.getData());
							offset += decode.length;
							output.write(decode);
						} 
						else {
							byte[] decode = read.getData().getBytes(StandardCharsets.UTF_8);
							offset += decode.length;
							output.write(decode);
						}
						if (read.getEof() == Boolean.TRUE) {
							break;
						}
					}
					while (true);
					result.setPdf(output.toByteArray());
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				finally {
					try {
						io.close(printToPDF.getStream());
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			else {
				// we explicitly try to clear all the templates, this is relevant for our own applications, less so for others
				try {
					runtime.evaluate("window.clearTemplates()");
				}
				catch (Exception e) {
					// doesn't matter if it failed...
					e.printStackTrace();
				}
				
				Evaluate evaluation = runtime.evaluate("document.documentElement.outerHTML");
				result.setContent((String) evaluation.getResult().getValue());
			}
			// Close the tab and close the browser when loading finishes.
			chromeService.closeTab(tab);
			devToolsService.close();
			launcher.close();

			return result;
		}
		catch (Throwable e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
//		page.addScriptToEvaluateOnLoad(arg0)
//		page.addScriptToEvaluateOnNewDocument(arg0)
	}
	
	public static ExecuteResult waitForStable(Page page, Runtime runtime, Long timeout, ExecuteResult result) {
		String targetState = "firstMeaningfulPaint";
//		String targetState = "firstContentfulPaint";
//		String targetState = "firstPaint";
		
		boolean waitForIdle = true;
		result.setRenderStart(new Date());
		try {
			boolean concluded = false;
			while(!concluded) {
				// we give it some time
				Thread.sleep(100);
				
				if (result.isPageBuilder()) {
					Evaluate evaluation = runtime.evaluate("application.services.page.stable");
					result.setStable(Boolean.TRUE.equals(evaluation.getResult().getValue()));
				}

				boolean idle = false;
				boolean loaded = false;
				// check if we reached the target state
				List<LifeCycleResult> lifeCycles = result.getLifeCycle();
				if (lifeCycles != null && !lifeCycles.isEmpty()) {
					lifeCycles = new ArrayList<LifeCycleResult>(lifeCycles);
					for (LifeCycleResult cycle : lifeCycles) {
						if (cycle.getName().equalsIgnoreCase(targetState)) {
							result.setStable(true);
						}
						else if (cycle.getName().equalsIgnoreCase("load")) {
							loaded = true;
						}
						// only network idles _after_ the load count
						else if (loaded && cycle.getName().equalsIgnoreCase("networkIdle")) {
							idle = true;
						}
					}
				}
				if (timeout != null && new Date().getTime() - result.getRenderStart().getTime() > timeout) {
					break;
				}
				
				// if we wait for an idle, we wait after it has been stabilized for a network idle event
				if (waitForIdle) {
					concluded = result.isStable() && idle;
				}
				else {
					concluded = result.isStable();
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		result.setRenderStop(new Date());
		return result;
	}
	
//	public String execute2(@WebParam(name = "method") String method, 
//			@NotNull @WebParam(name = "url") URI url, @WebParam(name = "part") Part part, @WebParam(name = "javascript") String javascript,
//			@WebParam(name = "timeout") Long timeout) throws InterruptedException {
//		
//		ensureThread();
//		
//		WebView webView = new WebView();
//		// can perhaps load html & javascript separately? then i can prepend the javascript with the one you provide...
//		// only thing to keep in mind in rest calls
////		webView.getEngine().executeScript(script)
//		
//		if (javascript != null) {
//			webView.getEngine().executeScript(javascript);
//		}
//		webView.getEngine().load(url.toString());
//		
//		CountDownLatch latch = new CountDownLatch(1);
//		webView.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
//			@Override
//			public void changed(ObservableValue<? extends State> observable, State oldValue, State newValue) {
//				if (newValue == State.SUCCEEDED) {
//					
//				}
//			}
//		});
//		if (timeout == null) {
//			timeout = 30000l;
//		}
//		latch.await(timeout, TimeUnit.MILLISECONDS);
//		return webView.getEngine().getDocument().getTextContent();
//	}
	
}
