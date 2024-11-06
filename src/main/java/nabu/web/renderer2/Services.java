/*
* Copyright (C) 2020 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kklisura.cdt.launch.ChromeArguments;
import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.protocol.commands.Fetch;
import com.github.kklisura.cdt.protocol.commands.IO;
import com.github.kklisura.cdt.protocol.commands.Network;
import com.github.kklisura.cdt.protocol.commands.Page;
import com.github.kklisura.cdt.protocol.commands.Runtime;
import com.github.kklisura.cdt.protocol.commands.Tracing;
import com.github.kklisura.cdt.protocol.events.console.MessageAdded;
import com.github.kklisura.cdt.protocol.events.fetch.RequestPaused;
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

import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.mime.impl.MimeUtils;
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
		
	private Logger logger = LoggerFactory.getLogger(getClass());
	
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
	
	public enum RenderLifeCycle {
		// can't wait for init
//		init,
		// or the dom content loaded, we check minimally that the load event was triggered
//		DOMContentLoaded,
		load,
		firstPaint,
		firstContentfulPaint,
		firstImagePaint,
		firstMeaningfulPaintCandidate,
		networkAlmostIdle,
		firstMeaningfulPaint,
		networkIdle,
		// custom
		pageBuilderStable
	}
	
	// TODO: return timing values as well
	// than we can do some performance testing as well!
	public ExecuteResult execute(@WebParam(name = "method") String method, 
			@NotNull @WebParam(name = "url") URI url, @WebParam(name = "part") Part part, @WebParam(name = "javascript") String javascript,
			@WebParam(name = "timeout") Long timeout, @WebParam(name = "timeoutUnit") TimeUnit timeUnit,
			@WebParam(name = "asPdf") Boolean asPdf,
			@WebParam(name = "waitFor") RenderLifeCycle lifecycle,
			@WebParam(name = "jwtToken") String jwtToken) {
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
		    		// chrome 111 changed some things to dev tools, it changed /json/new from a GET to a PUT and:
		    		// Please make sure to start Chrome with --remote-allow-origins=* or a specific origin. Chrome 111 no longer allows DevTools Websocket connections from arbitrary origins.
		    		.additionalArguments("remote-allow-origins", "*")
		    		.build();
		    
		    // Launch chrome either as headless (true) or regular (false).
	//	    ChromeService chromeService = launcher.launch(true);
		    ChromeService chromeService = launcher.launch(arguments);
		    
		    // Create empty tab ie about:blank.
		    ChromeTab tab = chromeService.createTab();
		    
			// Get DevTools service to this tab
		    ChromeDevToolsService devToolsService = chromeService.createDevToolsService(tab);
		    
		    
		    // print the javascript console
		    StringBuilder console = new StringBuilder();
		    devToolsService.getConsole().enable();
		    devToolsService.getConsole().onMessageAdded(new EventHandler<MessageAdded>() {
				@Override
				public void onEvent(MessageAdded arg0) {
					console.append(arg0.getMessage().getText()).append("\n");
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
			
			if (part != null || jwtToken != null) {
				// TODO: this is deprecated, you have to use Fetch.requestPaused instead
				Fetch fetch = devToolsService.getFetch();
				fetch.onRequestPaused(new EventHandler<RequestPaused>() {
					@Override
					public void onEvent(RequestPaused arg0) {
						logger.debug("Intercepting request: " + arg0.getRequest().getUrl());
						try {
							URI intercepted = new URI(arg0.getRequest().getUrl());
							// we only apply our logic to requests that are done to the same domain
							// if for instance you are including scripts from other domains, we don't want to enrich them
							if (url.getHost() != null && url.getHost().equals(intercepted.getHost())) {
								Map<String, Object> headers = arg0.getRequest().getHeaders();
								if (headers == null) {
									headers = new HashMap<String, Object>();
								}
								if (jwtToken != null) {
									headers.put("Authorization", "Bearer " + jwtToken);
								}
								if (part != null && part.getHeaders() != null) {
									for (Header header : part.getHeaders()) {
										headers.put(header.getName(), MimeUtils.getFullHeaderValue(header));
									}
								}
								arg0.getRequest().setHeaders(headers);
							}
						}
						catch (Exception e) {
							logger.error("Could not check intercepted message: " + arg0.getRequest().getUrl(), e);
						}
						finally {
							devToolsService.getFetch().continueRequest(arg0.getRequestId());
						}
					}
				});
				fetch.enable();
			}
			
//			network.onRequestIntercepted(new EventHandler<RequestIntercepted>() {
//				@Override
//				public void onEvent(RequestIntercepted arg0) {
//					
//				}
//			});

//			Tracing tracing = devToolsService.getTracing();
//			tracing.onDataCollected(
//			        event -> {
//			          if (event.getValue() != null) {
//			            System.out.println("----------------------------> trace: " + event.getValue());
//			          }
//			        });
//			tracing.start();
			
			if (javascript != null) {
	//			page.addScriptToEvaluateOnLoad(javascript);
				page.addScriptToEvaluateOnNewDocument(javascript);
			}
			
			// Log requests with onRequestWillBeSent event handler.
//			network.onRequestWillBeSent(new EventHandler<RequestWillBeSent>() {
//				@Override
//				public void onEvent(RequestWillBeSent event) {
//					System.out.printf(
//							"request: %s %s%s",
//							event.getRequest().getMethod(),
//							event.getRequest().getUrl(),
//							System.lineSeparator());
//				}
//			});
	
			// no good
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
			
//			network.onRequestIntercepted(new EventHandler<RequestIntercepted>() {
//				@Override
//				public void onEvent(RequestIntercepted event) {
//					String interceptionId = event.getInterceptionId();
//					Map<String, Object> headers = new HashMap<String, Object>();
//				}
//			});
			
			ExecuteResult result = new ExecuteResult();
			
			page.setLifecycleEventsEnabled(true);
			
			/*
			 * example of lifecycle states
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
//						System.out.println("--------------> lifecycle: " + arg0.getName());
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
			waitForStable(page, runtime, TimeUnit.MILLISECONDS.convert(timeout, timeUnit), result, lifecycle == null ? RenderLifeCycle.networkIdle : lifecycle);
			
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

			result.setConsoleLog(console.toString());
			return result;
		}
		catch (Throwable e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
//		page.addScriptToEvaluateOnLoad(arg0)
//		page.addScriptToEvaluateOnNewDocument(arg0)
	}
	
	public static ExecuteResult waitForStable(Page page, Runtime runtime, Long timeout, ExecuteResult result, RenderLifeCycle lifecycle) {
		String targetState = "firstMeaningfulPaint";
//		String targetState = "firstContentfulPaint";
//		String targetState = "firstPaint";
		
		boolean waitForIdle = true;
		result.setRenderStart(new Date());
		// we wait at least 500ms until after the stable state is reached
		// this was gleaned as a potentially good timeout from an article, they indicated that playwright waits 500ms after "networkIdle"
		long stateStability = 500;
		try {
			boolean concluded = false;
			while(!concluded) {
				// we give it some time
				Thread.sleep(100);
				
				Date date = new Date();
				// currently we don't use this
				// it only scales to our own applications and should only be used as a last resort
				if (lifecycle.equals(RenderLifeCycle.pageBuilderStable) && result.isPageBuilder() && !result.isStable()) {
					Evaluate evaluation = runtime.evaluate("application.services.page.stable");
					result.setStable(Boolean.TRUE.equals(evaluation.getResult().getValue()));
					if (result.isStable()) {
						result.setRenderReady(date);
					}
				}

				boolean idle = false;
				boolean loaded = false;
				// check if we reached the target state
				List<LifeCycleResult> lifeCycles = result.getLifeCycle();
				if (lifeCycles != null && !lifeCycles.isEmpty()) {
					lifeCycles = new ArrayList<LifeCycleResult>(lifeCycles);
					for (LifeCycleResult cycle : lifeCycles) {
						// only update this if the result is not stable yet
						// otherwise, we keep moving the render ready in the future, never quite achieving nirvana
						if (cycle.getName().equalsIgnoreCase(lifecycle.name()) && !result.isStable()) {
							// we only count lifecycles after the load
							// unless you are interested in the load itself of course...
							if (lifecycle.equals(RenderLifeCycle.load) || loaded) {
								result.setRenderReady(date);
								result.setStable(true);
							}
						}
						// we had an edge where the waiting for event was triggered before load
						// after load, we had to run the timer to get the result back (event hit at 31s, load at 32s, render timeout at 60s)
						// not sure if there is a logic gap here?
						else if (cycle.getName().equalsIgnoreCase("load")) {
							loaded = true;
						}
						// only network idles _after_ the load count
						else if (loaded && cycle.getName().equalsIgnoreCase(RenderLifeCycle.networkIdle.name())) {
							idle = true;
						}
					}
				}
				// if we have waited too long, break out
				if (timeout != null && date.getTime() - result.getRenderStart().getTime() > timeout) {
					break;
				}
				// if we have achieved render readiness and have waited some additional time, we consider it done
				if (result.getRenderReady() != null && date.getTime() > result.getRenderReady().getTime() + stateStability) {
					break;
				}
				// if we wait for an idle, we wait after it has been stabilized for a network idle event
//				if (waitForIdle) {
//					concluded = result.isStable() && idle;
//				}
//				else {
//					concluded = result.isStable();
//				}
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
