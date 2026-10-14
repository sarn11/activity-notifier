package com.activitynotifier.ui;

import com.activitynotifier.ActivityNotifierConfig;
import com.activitynotifier.detection.CustomAnimations;
import com.activitynotifier.detection.Detector;
import com.activitynotifier.resources.ResourceType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Quick settings (FR-027). Every control reads from and writes to {@link ConfigManager}, so the panel and
 * the config screen share one source of truth (FR-028). EDT only; never touches the client.
 */
public class ActivityNotifierPanel extends PluginPanel
{
	private static final int SPINNER_WIDTH = 72;
	private static final int MAX_DELAY_MS = 600_000;
	private static final int DELAY_STEP_MS = 100;
	private static final int MAX_POINTS = 9_999;
	private static final int MAX_PERCENT = 100;
	private static final String NO_ANIMATION = "-";

	private final ConfigManager configManager;
	private final ActivityNotifierConfig config;

	private final JSpinner baseDelay = spinner(0, MAX_DELAY_MS, DELAY_STEP_MS);
	private final JCheckBox chatSignals = new JCheckBox("Chat messages");
	private final JCheckBox notificationSignals = new JCheckBox("RuneLite notification");
	private final Map<Detector, JCheckBox> detectorBoxes = new EnumMap<>(Detector.class);
	private final Map<Detector, JSpinner> delaySpinners = new EnumMap<>(Detector.class);
	private final Map<ResourceType, JCheckBox> resourceBoxes = new EnumMap<>(ResourceType.class);
	private final Map<ResourceType, JSpinner> thresholdSpinners = new EnumMap<>(ResourceType.class);
	private final JCheckBox damageBox = new JCheckBox("Damage taken");
	private final JSpinner damageMinimum = spinner(1, MAX_POINTS, 1);
	private final JTextField lastAnimationId = new JTextField(NO_ANIMATION);
	private final JLabel lastAnimationStatus = new JLabel();

	/**
	 * The last animation the player started, for finding IDs to add to Custom animation IDs; -1 until the
	 * first one.
	 */
	private int lastAnimation = -1;

	/**
	 * Set while {@link #refresh} copies config into the controls, so that doesn't write config back.
	 */
	private boolean refreshing;

	public ActivityNotifierPanel(ConfigManager configManager, ActivityNotifierConfig config)
	{
		this.configManager = configManager;
		this.config = config;

		setLayout(new GridBagLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTH;

		add(title("Activity Notifier"), c);
		c.gridy++;

		add(section("General", "ms", generalRows()), c);
		c.gridy++;
		add(section("Signals", null, signalsRows()), c);
		c.gridy++;
		add(section("General detectors", "Extra ms", detectorRows(Detector.Group.GENERAL)), c);
		c.gridy++;
		add(section("Specific activities", "Extra ms", detectorRows(Detector.Group.SPECIFIC)), c);
		c.gridy++;
		add(section("Resource alerts", "Threshold", resourceRows()), c);
		c.gridy++;
		add(syncNote(), c);
		c.gridy++;

		// push everything to the top
		c.weighty = 1;
		add(new JPanel(), c);

		refresh();
	}

	/**
	 * Re-reads every control from config (config changed elsewhere, or profile switched).
	 */
	public void refresh()
	{
		refreshing = true;
		try
		{
			baseDelay.setValue(Math.max(0, config.baseDelay()));
			chatSignals.setSelected(config.chatSignals());
			notificationSignals.setSelected(config.notificationSignals().isEnabled());
			for (Detector detector : Detector.values())
			{
				detectorBoxes.get(detector).setSelected(detector.isEnabled(configManager));
				delaySpinners.get(detector).setValue(detector.extraDelayMs(configManager));
			}
			for (ResourceType type : ResourceType.values())
			{
				resourceBoxes.get(type).setSelected(type.isEnabled(configManager));
				thresholdSpinners.get(type).setValue(type.threshold(configManager));
			}
			damageBox.setSelected(config.alertDamage());
			damageMinimum.setValue(Math.max(1, config.damageThreshold()));
			// the custom list may have changed, which changes how the last animation counts
			showLastAnimation();
		}
		finally
		{
			refreshing = false;
		}
	}

	/**
	 * Shows the animation the player just started. Called on the EDT.
	 */
	public void setLastAnimation(int animation)
	{
		lastAnimation = animation;
		showLastAnimation();
	}

	private void showLastAnimation()
	{
		if (lastAnimation == -1)
		{
			lastAnimationId.setText(NO_ANIMATION);
			lastAnimationStatus.setText("None yet");
			return;
		}

		lastAnimationId.setText(String.valueOf(lastAnimation));
		String countedAs = CustomAnimations.countedAs(lastAnimation, CustomAnimations.parse(config.customAnimationIds()));
		lastAnimationStatus.setText(CustomAnimations.NOT_COUNTED.equals(countedAs) ? countedAs : "Counts as " + countedAs);
	}

	private JPanel generalRows()
	{
		JLabel label = new JLabel("Base delay");
		label.setToolTipText("How long no activity must be seen before you count as inactive, before extra delays");
		baseDelay.setToolTipText("Base delay, in milliseconds");
		baseDelay.addChangeListener(e -> set("baseDelay", baseDelay.getValue()));

		JPanel rows = rowsPanel();
		addRow(rows, 0, label, baseDelay);
		return rows;
	}

	private JPanel signalsRows()
	{
		chatSignals.setToolTipText("Send signals as filtered game messages (Watchdog: Game Message alert, type Spam or Any)");
		chatSignals.addActionListener(e -> set("chatSignals", chatSignals.isSelected()));

		notificationSignals.setToolTipText("Send signals as RuneLite notifications (Watchdog: Notification Fired alert)");
		// keep the user's other notification options (sound, tray, focus) and only flip enabled
		notificationSignals.addActionListener(e ->
			set("notificationSignals", config.notificationSignals().withEnabled(notificationSignals.isSelected())));

		JPanel rows = rowsPanel();
		addRow(rows, 0, chatSignals, null);
		addRow(rows, 1, notificationSignals, null);
		return rows;
	}

	private JPanel detectorRows(Detector.Group group)
	{
		JPanel rows = rowsPanel();
		int row = 0;
		for (Detector detector : Detector.values())
		{
			if (detector.getGroup() != group)
			{
				continue;
			}

			JCheckBox box = new JCheckBox(detector.getDisplayName() + (detector.isBeta() ? " (beta)" : ""));
			box.addActionListener(e -> set(detector.getEnabledKey(), box.isSelected()));

			JSpinner spinner = spinner(0, MAX_DELAY_MS, DELAY_STEP_MS);
			spinner.setToolTipText("Extra delay for " + detector.getDisplayName() + ", in milliseconds");
			spinner.addChangeListener(e -> set(detector.getDelayKey(), spinner.getValue()));

			detectorBoxes.put(detector, box);
			delaySpinners.put(detector, spinner);
			addRow(rows, row++, box, spinner);
		}

		if (group == Detector.Group.GENERAL)
		{
			// read-only, but selectable so the ID can be copied into Custom animation IDs
			String tooltip = "The last animation you played, and whether it counts as activity. "
				+ "Copy the ID into Custom animation IDs to make it count";
			JLabel label = new JLabel("Last animation");
			label.setToolTipText(tooltip);
			lastAnimationId.setEditable(false);
			lastAnimationId.setHorizontalAlignment(JTextField.RIGHT);
			lastAnimationId.setPreferredSize(new Dimension(SPINNER_WIDTH, lastAnimationId.getPreferredSize().height));
			lastAnimationId.setToolTipText(tooltip);
			lastAnimationStatus.setFont(FontManager.getRunescapeSmallFont());
			lastAnimationStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			lastAnimationStatus.setToolTipText(tooltip);
			addRow(rows, row++, label, lastAnimationId);
			addRow(rows, row++, lastAnimationStatus, null);
		}
		return rows;
	}

	private JPanel resourceRows()
	{
		JPanel rows = rowsPanel();
		int row = 0;
		for (ResourceType type : ResourceType.values())
		{
			JCheckBox box = new JCheckBox(type.getAlertKeyword());
			box.addActionListener(e -> set(type.getEnabledKey(), box.isSelected()));

			boolean percent = type.getUnit() == ResourceType.Unit.PERCENT;
			JSpinner spinner = spinner(0, percent ? MAX_PERCENT : MAX_POINTS, 1);
			spinner.setToolTipText(type.getAlertKeyword() + " threshold" + (percent ? ", in percent" : ""));
			spinner.addChangeListener(e -> set(type.getThresholdKey(), spinner.getValue()));

			resourceBoxes.put(type, box);
			thresholdSpinners.put(type, spinner);
			addRow(rows, row++, box, spinner);
		}

		// cooldown and damage over time stay in the settings page
		damageBox.setToolTipText("Send a signal when you take damage of at least the minimum");
		damageBox.addActionListener(e -> set("alertDamage", damageBox.isSelected()));
		damageMinimum.setToolTipText("Minimum damage of a single hit");
		damageMinimum.addChangeListener(e -> set("damageThreshold", damageMinimum.getValue()));
		addRow(rows, row++, damageBox, damageMinimum);
		return rows;
	}

	private <T> void set(String key, T value)
	{
		if (!refreshing)
		{
			configManager.setConfiguration(ActivityNotifierConfig.GROUP, key, value);
		}
	}

	private static JSpinner spinner(int min, int max, int step)
	{
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
		Dimension size = spinner.getPreferredSize();
		spinner.setPreferredSize(new Dimension(SPINNER_WIDTH, size.height));
		return spinner;
	}

	private static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return label;
	}

	/**
	 * RuneLite's settings page only re-reads values when it is opened, so changes made here show up
	 * there the next time it is opened. They still apply immediately.
	 */
	private static JLabel syncNote()
	{
		JLabel note = new JLabel("<html>Changes apply immediately. If the plugin's settings page is open, "
			+ "reopen it to see them there.</html>");
		note.setFont(FontManager.getRunescapeSmallFont());
		note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		note.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		return note;
	}

	private static JPanel section(String name, String columnHeader, JPanel rows)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(0, 0, 3, 0)));

		JLabel title = new JLabel(name);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.TEXT_COLOR);
		header.add(title, BorderLayout.WEST);

		if (columnHeader != null)
		{
			JLabel column = new JLabel(columnHeader, SwingConstants.RIGHT);
			column.setFont(FontManager.getRunescapeSmallFont());
			column.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			header.add(column, BorderLayout.EAST);
		}

		JPanel section = new JPanel(new BorderLayout());
		section.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		section.add(header, BorderLayout.NORTH);
		section.add(rows, BorderLayout.CENTER);
		return section;
	}

	private static JPanel rowsPanel()
	{
		JPanel rows = new JPanel(new GridBagLayout());
		rows.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		return rows;
	}

	private static void addRow(JPanel rows, int row, JComponent left, JComponent right)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = row;
		// without a right-hand control, the left one can use the full width
		c.gridwidth = right == null ? 2 : 1;
		c.insets = new Insets(1, 0, 1, 0);
		c.anchor = GridBagConstraints.WEST;

		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		rows.add(left, c);

		if (right != null)
		{
			c.gridx = 1;
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.EAST;
			rows.add(right, c);
		}
	}
}
