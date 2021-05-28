package nabu.web.renderer2;

import java.net.URI;
import java.util.ArrayList;
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
	
	// TODO: return timing values as well
	// than we can do some performance testing as well!
	public ExecuteResult execute(@WebParam(name = "method") String method, 
			@NotNull @WebParam(name = "url") URI url, @WebParam(name = "part") Part part, @WebParam(name = "javascript") String javascript,
			@WebParam(name = "timeout") Long timeout, @WebParam(name = "timeoutUnit") TimeUnit timeUnit) {
		
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
			
			
			String targetState = "firstMeaningfulPaint";
	//		String targetState = "firstContentfulPaint";
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
						
						boolean stop = false;
						// we wait for the meaningfulcontent
						if (targetState.equals(arg0.getName())) {
							if (waitForNetworkIdle) {
								state.add(true);
							}
							else {
								stop = true;
							}
						}
						else if (waitForNetworkIdle && "networkIdle".equals(arg0.getName()) && !state.isEmpty()) {
							stop = true;
						}
						if (stop) {
	//						try {
	//							Thread.sleep(5000);
	//						} catch (InterruptedException e) {
	//							// TODO Auto-generated catch block
	//							e.printStackTrace();
	//						}
							try {
								Runtime runtime = devToolsService.getRuntime();
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
								// Close the tab and close the browser when loading finishes.
								chromeService.closeTab(tab);
								launcher.close();
							}
							finally {
								latch.countDown();
							}
						}
					}
					catch (Throwable e) {
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
			
			// enable page events
			page.enable();
			
			page.navigate(url.toString());
	
//			devToolsService.waitUntilClosed();
			if (timeout == null) {
				timeout = 30l;
				timeUnit = TimeUnit.SECONDS;
			}
			latch.await(timeout, timeUnit == null ? TimeUnit.MILLISECONDS : timeUnit);

			return result;
		}
		catch (Throwable e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
//		page.addScriptToEvaluateOnLoad(arg0)
//		page.addScriptToEvaluateOnNewDocument(arg0)
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
