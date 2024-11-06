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

package nabu.web.renderer2.types;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ExecuteResult {
	private String content;
	private byte[] pdf;
	private List<LifeCycleResult> lifeCycle = new ArrayList<LifeCycleResult>();
	private boolean pageBuilder, stable;
	private Date renderStart, renderReady, renderStop;
	private String consoleLog;
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
	public Date getRenderReady() {
		return renderReady;
	}
	public void setRenderReady(Date renderReady) {
		this.renderReady = renderReady;
	}
	public String getConsoleLog() {
		return consoleLog;
	}
	public void setConsoleLog(String consoleLog) {
		this.consoleLog = consoleLog;
	}
}
