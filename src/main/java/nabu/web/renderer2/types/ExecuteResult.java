package nabu.web.renderer2.types;

import java.util.ArrayList;
import java.util.List;

public class ExecuteResult {
	private String content;
	private List<LifeCycleResult> lifeCycle = new ArrayList<LifeCycleResult>();
	public static class LifeCycleResult {
		private String name;
		private long time;
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public long getTime() {
			return time;
		}
		public void setTime(long time) {
			this.time = time;
		}
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public List<LifeCycleResult> getLifeCycle() {
		return lifeCycle;
	}
	public void setLifeCycle(List<LifeCycleResult> lifeCycle) {
		this.lifeCycle = lifeCycle;
	}
}
