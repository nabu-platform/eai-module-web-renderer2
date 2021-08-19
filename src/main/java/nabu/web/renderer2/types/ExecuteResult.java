package nabu.web.renderer2.types;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ExecuteResult {
	private String content;
	private byte[] pdf;
	private List<LifeCycleResult> lifeCycle = new ArrayList<LifeCycleResult>();
	private boolean pageBuilder, stable;
	private Date renderStart, renderStop;
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
	public boolean isPageBuilder() {
		return pageBuilder;
	}
	public void setPageBuilder(boolean pageBuilder) {
		this.pageBuilder = pageBuilder;
	}
	public boolean isStable() {
		return stable;
	}
	public void setStable(boolean stable) {
		this.stable = stable;
	}
	public Date getRenderStart() {
		return renderStart;
	}
	public void setRenderStart(Date renderStart) {
		this.renderStart = renderStart;
	}
	public Date getRenderStop() {
		return renderStop;
	}
	public void setRenderStop(Date renderStop) {
		this.renderStop = renderStop;
	}
	public byte[] getPdf() {
		return pdf;
	}
	public void setPdf(byte[] pdf) {
		this.pdf = pdf;
	}
}
