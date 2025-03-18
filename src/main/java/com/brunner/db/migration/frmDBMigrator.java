package com.brunner.db.migration;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import com.fasterxml.jackson.databind.JsonNode;

public class frmDBMigrator extends JFrame {
	public static void main(String[] args) {
		try {
			// logger.info("EIF Simulator for RMSPlus started.");

			frmDBMigrator fMigrator = new frmDBMigrator();
			fMigrator.initLayout();

			fMigrator.setTitle("DB Table Migrator");
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, e.getStackTrace(), "Exception",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private static final long serialVersionUID = 1L;

	JLabel lblSourceConnectionInfo = null;
	JTextArea txtSourceConnectionInfo = null;
	JScrollPane spSourceConnectionInfo = null;

	JLabel lblTargetConnectionInfo = null;
	JTextArea txtTargetConnectionInfo = null;

	JTabbedPane tabMain = null;
	JPanel pnlMain = null;

	JLabel lblTableList = null;
	public JTable tblTableList = null;
	JScrollPane spTableList = null;

	JLabel lblLogList = null;
	JList<LogEntry> lstLogList = null;
	JScrollPane spLogList = null;

	JButton btnClearLog = null;
	JButton btnClose = null;

	JButton btnReload = null;
	JButton btnStart = null;
	JButton btnStop = null;

	Migrator migration = null;
	ConfigLoader loader = null;

	public frmDBMigrator() {
		super("RMS DB Migration");
	}

	void initLayout() {
		this.setSize(1280, 820);
		center();
		setResizable(false);
		setLayout(null);

		lblSourceConnectionInfo = new JLabel("Source DB");
		lblSourceConnectionInfo.setVisible(true);
		lblSourceConnectionInfo.setSize(100, 50);
		lblSourceConnectionInfo.setLocation(20, 10);
		add(lblSourceConnectionInfo);

		txtSourceConnectionInfo = new JTextArea("");
		txtSourceConnectionInfo.setVisible(true);
		txtSourceConnectionInfo.setEnabled(true);
		txtSourceConnectionInfo.setLineWrap(true);
		spSourceConnectionInfo = new JScrollPane(txtSourceConnectionInfo, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		spSourceConnectionInfo.setBounds(100, 20, 1150, 50);
		add(spSourceConnectionInfo);

		lblTargetConnectionInfo = new JLabel("Target DB");
		lblTargetConnectionInfo.setVisible(true);
		lblTargetConnectionInfo.setSize(100, 50);
		lblTargetConnectionInfo.setLocation(20, 55);
		add(lblTargetConnectionInfo);

		txtTargetConnectionInfo = new JTextArea("");
		txtTargetConnectionInfo.setVisible(true);
		txtTargetConnectionInfo.setEnabled(true);
		txtTargetConnectionInfo.setLineWrap(true);
		spSourceConnectionInfo = new JScrollPane(txtTargetConnectionInfo, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		spSourceConnectionInfo.setBounds(100, 70, 1150, 50);
		add(spSourceConnectionInfo);

		tabMain = new JTabbedPane();

		pnlMain = new JPanel();
		pnlMain.setLayout(null);
		// pnlMain.setBackground(Color.YELLOW);

		lblTableList = new JLabel("Table List");
		lblTableList.setVisible(true);
		lblTableList.setSize(100, 25);
		lblTableList.setLocation(10, 10);

		pnlMain.add(lblTableList);

		tblTableList = new JTable();
		tblTableList.setModel(new ReadOnlyTableModel());
		tblTableList.setVisible(true);
		tblTableList.setEnabled(true);

		spTableList = new JScrollPane(tblTableList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		spTableList.setBounds(10, 40, 1200, 230);
		pnlMain.add(spTableList);

		lblLogList = new JLabel("Log List");
		lblLogList.setVisible(true);
		lblLogList.setSize(100, 25);
		lblLogList.setLocation(10, 300);

		pnlMain.add(lblLogList);
		lstLogList = new JList<>(new DefaultListModel<LogEntry>());
		lstLogList.setCellRenderer(new LogListCellRenderer());
		lstLogList.setVisible(true);
		lstLogList.setEnabled(true);

		spLogList = new JScrollPane(lstLogList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		spLogList.setBounds(10, 330, 1200, 230);
		pnlMain.add(spLogList);

		DefaultListModel<LogEntry> logListModel = (DefaultListModel<LogEntry>) lstLogList.getModel();
		lstLogList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					int index = lstLogList.locationToIndex(e.getPoint());
					if (index >= 0) {
						String log = logListModel.getElementAt(index).toString();
						String[] parts = log.split(":");
						String time = parts[0];
						String message = log.replace(parts[0] + ":", "");
						JOptionPane.showMessageDialog(null, message, time, JOptionPane.INFORMATION_MESSAGE);
					}

				}
			}
		});

		btnClearLog = new JButton("Clear");
		btnClearLog.setVisible(true);
		btnClearLog.setEnabled(true);
		btnClearLog.setLocation(1045, 565);
		btnClearLog.setSize(80, 25);

		btnClearLog.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				btnClearLog_Click();
			}

		});

		pnlMain.add(btnClearLog);

		btnClose = new JButton("Close");
		btnClose.setVisible(true);
		btnClose.setEnabled(true);
		btnClose.setLocation(1130, 565);
		btnClose.setSize(80, 25);

		btnClose.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent arg0) {
				try {
				} finally {
					System.exit(0);
				}
			}

		});

		pnlMain.add(btnClose);

		pnlMain.setLocation(new Point(10, 520));
		pnlMain.setSize(new Dimension(1240, 65));
		tabMain.addTab("Migration tables", pnlMain);

		btnReload = new JButton("Reload");
		btnReload.setVisible(true);
		btnReload.setEnabled(true);
		btnReload.setLocation(960, 280);
		btnReload.setSize(80, 25);
		btnReload.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				btnReload_Click();
			}

		});

		pnlMain.add(btnReload);

		pnlMain.setLocation(new Point(10, 520));
		pnlMain.setSize(new Dimension(1240, 65));
		tabMain.addTab("Migration tables", pnlMain);

		btnStart = new JButton("Start");
		btnStart.setVisible(true);
		btnStart.setEnabled(true);
		btnStart.setLocation(1045, 280);
		btnStart.setSize(80, 25);
		btnStart.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				btnStart_Click();
			}

		});

		pnlMain.add(btnStart);

		btnStop = new JButton("Stop");
		btnStop.setVisible(true);
		btnStop.setEnabled(true);
		btnStop.setLocation(1130, 280);
		btnStop.setSize(80, 25);
		btnStop.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				btnStop_Click();
			}

		});
		pnlMain.add(btnStop);

		tabMain.setVisible(true);
		tabMain.setLocation(20, 145);
		tabMain.setSize(1230, 630);
		add(tabMain);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});

		initialize();

		setVisible(true);
	}

	void initialize() {
		try {
			txtSourceConnectionInfo.setText("");
			txtTargetConnectionInfo.setText("");

			try {
			loader = new ConfigLoader("config.json");
			}
			catch (Exception e) {
				AddLog("Failed to load config.json", Color.RED);
			}
			migration = new Migrator(this, loader.getConfig());
			txtSourceConnectionInfo.setText(loader.getConfig().get("source_db").toString());
			txtSourceConnectionInfo.setEditable(false);

			txtTargetConnectionInfo.setText(loader.getConfig().get("target_db").toString());
			txtTargetConnectionInfo.setEditable(false);

			// tables 요소의 속성 목록의 합집합으로 tblTableList에 초기 컬럼 헤더 설정
			Set<String> columnHeaders = new HashSet<>();
			for (JsonNode table : loader.getConfig().get("tables")) {
				for (int index = 0; index < table.size(); index++) {
					table.fieldNames().forEachRemaining(fieldName -> {
						if (!columnHeaders.contains(fieldName))
							columnHeaders.add(fieldName);
					});
				}
			}

			tblTableList.setModel(new ReadOnlyTableModel());

			ReadOnlyTableModel model = (ReadOnlyTableModel) tblTableList.getModel();
			model.setColumnIdentifiers(columnHeaders.toArray());

			// tables 요소의 실제 값들을 읽어서 행을 추가
			for (JsonNode table : loader.getConfig().get("tables")) {
				Object[] rowData = new Object[columnHeaders.size()];
				int i = 0;
				for (String column : columnHeaders) {
					JsonNode value = table.get(column);
					rowData[i++] = (value != null) ? value.asText() : "";
				}
				model.addRow(rowData);
			}
		} catch (Exception e) {
			AddLog(e);
		}

		// 각 컬럼의 폭을 조정
		TableColumnModel columnModel = tblTableList.getColumnModel();
		for (int col = 0; col < columnModel.getColumnCount(); col++) {
			TableColumn column = columnModel.getColumn(col);
			String columnName = column.getHeaderValue().toString();

			// 컬럼 이름에 따라 폭 설정
			if (columnName.equals("source_tb") || columnName.equals("target_tb")) {
				column.setMinWidth(50);
				column.setPreferredWidth(200);
				column.setMaxWidth(400);

				if (columnName.equals("source_tb"))
					columnModel.moveColumn(columnModel.getColumnIndex(columnName), 0);
				if (columnName.equals("target_tb"))
					columnModel.moveColumn(columnModel.getColumnIndex(columnName), 1);

			} else if (columnName.equals("batch_size") || columnName.equals("skip_flag")) {
				column.setMinWidth(30);
				column.setPreferredWidth(50);
				column.setMaxWidth(70);

				if (columnName.equals("skip_flag"))
					columnModel.moveColumn(columnModel.getColumnIndex(columnName), 2);
			} else {
				column.setMinWidth(50); // 기본 최소 폭 설정
				column.setPreferredWidth(80); // 기본 폭 설정
				column.setMaxWidth(120); // 기본 최대 폭 설정
			}
		}

		// 컬럼 이름에 따라 위치 설정
		columnModel.moveColumn(columnModel.getColumnIndex("on_error"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("trunc_target_tb"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("range_to"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("range_from"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("range_type"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("range_column"), 0);
		// columnModel.moveColumn(columnModel.getColumnIndex("batch_size"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("skip_flag"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("target_tb"), 0);
		columnModel.moveColumn(columnModel.getColumnIndex("source_tb"), 0);
	}

	void btnClearLog_Click() {
		if (((DefaultListModel<LogEntry>) lstLogList.getModel()).getSize() == 0)
			return;
		int response = JOptionPane.showConfirmDialog(null, "Clear all the logs?", "확인", JOptionPane.YES_NO_OPTION);
		if (response == JOptionPane.YES_OPTION) {
			DefaultListModel<LogEntry> model = (DefaultListModel<LogEntry>) lstLogList.getModel();
			model.clear();
		}
	}

	private void center() {
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		int w = getSize().width;
		int h = getSize().height;
		int x = (dim.width - w) / 2;
		int y = (dim.height - h) / 2;

		// Move the window
		setLocation(x, y);
	}

	void btnReload_Click() {
		btnClearLog_Click();

		Thread th = new Thread(new Runnable() {
			public void run() {
				try {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							try {
								initialize();
							} catch (Exception e) {
								AddLog(e);
							}
						}
					});
				} catch (Exception e) {
					AddLog(e);
				}
			}
		});

		th.setPriority(Thread.MAX_PRIORITY);
		th.start();
	}

	void btnStart_Click() {

		Thread th = new Thread(new Runnable() {
			public void run() {
				try {
					migration.migrateAllTables();
				} catch (Exception e) {
					AddLog(e);
				} finally {
					if (migration != null)
						try {
							migration.closeConnections();
						} catch (Exception e) {
							AddLog(e);
						} finally {
							btnStop.setEnabled(false);
							btnStart.setEnabled(true);
							btnReload.setEnabled(true);
						}
				}
			}
		});

		th.setPriority(Thread.MAX_PRIORITY);
		th.start();

		btnStart.setEnabled(false);
		btnStop.setEnabled(true);
		btnReload.setEnabled(false);
	}

	void btnStop_Click() {
		if (migration != null) {
			int response = JOptionPane.showConfirmDialog(null, "Stop immediately?", "확인", JOptionPane.YES_NO_OPTION);
			if (response == JOptionPane.YES_OPTION)
				migration.stopMigration();

			btnStop.setEnabled(false);
			btnStart.setEnabled(true);
			btnReload.setEnabled(true);
		}
	}

	public void AddLog(String log, Color color) {
		SwingUtilities.invokeLater(() -> {
			DefaultListModel<LogEntry> model = (DefaultListModel<LogEntry>) lstLogList.getModel();
			String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
			LogEntry logEntry = new LogEntry(timeStamp, log, color);
			model.addElement(logEntry);
			lstLogList.ensureIndexIsVisible(model.getSize() - 1);
		});
	}

	class LogEntry {
		private String timeStamp;
		private String log;
		private Color color;

		public LogEntry(String timeStamp, String log, Color color) {
			this.timeStamp = timeStamp;
			this.log = log;
			this.color = color;
		}

		@Override
		public String toString() {
			return timeStamp + ": " + log;
		}

		public Color getColor() {
			return color;
		}
	}

	void AddLog(Exception e) {
		AddLog(String.format("%s", Migrator.getFullErrorMessage(e)), Color.RED);
	}

	public JTable getTable() {
		return tblTableList;
	}

	// 커스텀 셀 렌더러 클래스
	class LogListCellRenderer extends DefaultListCellRenderer {
		@Override
		public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus) {
			java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof LogEntry) {
				LogEntry logEntry = (LogEntry) value;
				c.setForeground(logEntry.getColor());
			}
			return c;
		}
	}

	// 읽기 전용 테이블 모델 클래스
	public class ReadOnlyTableModel extends DefaultTableModel {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	}
}