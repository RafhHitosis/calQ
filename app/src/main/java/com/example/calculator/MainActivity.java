package com.example.calculator;

import android.app.Dialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Room;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Stack;

// Room Database Entity
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.RoomDatabase;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

// Room Database Entity
@Entity(tableName = "calculation_history")
class CalculationHistory {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "expression")
    public String expression;

    @ColumnInfo(name = "result")
    public String result;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    public CalculationHistory(String expression, String result, long timestamp) {
        this.expression = expression;
        this.result = result;
        this.timestamp = timestamp;
    }
}

// Room Database DAO
@Dao
interface CalculationHistoryDao {
    @Insert
    void insert(CalculationHistory calculation);

    @Query("SELECT * FROM calculation_history ORDER BY timestamp DESC LIMIT 50")
    List<CalculationHistory> getRecentCalculations();

    @Query("SELECT * FROM calculation_history ORDER BY timestamp DESC")
    List<CalculationHistory> getAllCalculations();

    @Query("SELECT * FROM calculation_history WHERE expression LIKE :searchQuery OR result LIKE :searchQuery ORDER BY timestamp DESC LIMIT 50")
    List<CalculationHistory> searchCalculations(String searchQuery);

    @Query("DELETE FROM calculation_history WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM calculation_history")
    void clearHistory();

    @Query("SELECT COUNT(*) FROM calculation_history")
    int getHistoryCount();
}

// Room Database
@Database(entities = {CalculationHistory.class}, version = 1, exportSchema = false)
abstract class CalculationDatabase extends RoomDatabase {
    public abstract CalculationHistoryDao calculationHistoryDao();

    private static volatile CalculationDatabase INSTANCE;

    static CalculationDatabase getDatabase(final android.content.Context context) {
        if (INSTANCE == null) {
            synchronized (CalculationDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    CalculationDatabase.class, "calculation_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

// History Adapter for RecyclerView
class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private List<CalculationHistory> historyList = new ArrayList<>();
    private List<CalculationHistory> filteredHistoryList = new ArrayList<>();
    private OnHistoryItemClickListener listener;
    private OnHistoryItemActionListener actionListener;
    private boolean isLandscapeMode = false;
    private Set<Integer> pendingDeletions = new HashSet<>();

    public List<CalculationHistory> getFilteredHistoryList() {
        return new ArrayList<>(filteredHistoryList);
    }

    public boolean isDeletionPending(int itemId) {
        return pendingDeletions.contains(itemId);
    }

    public void markDeletionPending(int itemId) {
        pendingDeletions.add(itemId);
    }

    public void markDeletionComplete(int itemId) {
        pendingDeletions.remove(itemId);
    }

    public interface OnHistoryItemClickListener {
        void onHistoryItemClick(String result);
        void onExpressionClick(String expression);
    }

    public interface OnHistoryItemActionListener {
        void onCopyResult(String result);
        void onDeleteItem(CalculationHistory item, int position);
        void onUseExpression(String expression);
    }

    public void setLandscapeMode(boolean landscapeMode) {
        this.isLandscapeMode = landscapeMode;
    }

    public void setOnHistoryItemClickListener(OnHistoryItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnHistoryItemActionListener(OnHistoryItemActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setHistoryList(List<CalculationHistory> historyList) {
        this.historyList = historyList;
        this.filteredHistoryList = new ArrayList<>(historyList);
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < filteredHistoryList.size()) {
            CalculationHistory removedItem = filteredHistoryList.get(position);
            filteredHistoryList.remove(position);

            // Also remove from the main list if it exists there
            historyList.removeIf(item -> item.id == removedItem.id);

            notifyItemRemoved(position);

            // If the list is now empty, trigger a refresh to show empty state
            if (filteredHistoryList.isEmpty()) {
                notifyDataSetChanged();
            }
        }
    }

    public void filter(String searchText) {
        filteredHistoryList.clear();
        if (searchText.isEmpty()) {
            filteredHistoryList.addAll(historyList);
        } else {
            String searchLower = searchText.toLowerCase();
            for (CalculationHistory item : historyList) {
                if (item.expression.toLowerCase().contains(searchLower) ||
                        item.result.toLowerCase().contains(searchLower)) {
                    filteredHistoryList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.history_item_modern, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        CalculationHistory calculation = filteredHistoryList.get(position);

        // Set the expression and result
        holder.expressionText.setText(calculation.expression);
        holder.resultText.setText("= " + calculation.result);

        // Format and set timestamp
        holder.timestampText.setText(getRelativeTimeString(calculation.timestamp));

        // Handle quick actions in landscape mode

        if (holder.quickActionButtons != null) {
            holder.quickActionButtons.setVisibility(View.VISIBLE);
            setupQuickActions(holder, calculation, position);
        }

        holder.actionButtonsContainer.setVisibility(View.GONE);

        holder.itemView.setOnLongClickListener(v -> {
            boolean isVisible = holder.actionButtonsContainer.getVisibility() == View.VISIBLE;
            holder.actionButtonsContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            return true;
        });

        setupStandardListeners(holder, calculation);
    }

    private void setupQuickActions(HistoryViewHolder holder, CalculationHistory calculation, int position) {
        if (holder.quickDeleteButton != null) {
            holder.quickDeleteButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    // Use the actual item from filteredHistoryList instead of position
                    int currentPosition = holder.getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION && currentPosition < filteredHistoryList.size()) {
                        CalculationHistory itemToDelete = filteredHistoryList.get(currentPosition);
                        actionListener.onDeleteItem(itemToDelete, currentPosition);
                    }
                }
            });
        }

        if (holder.quickUseButton != null) {
            holder.quickUseButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onUseExpression(calculation.expression);
                }
            });
        }

        if (holder.deleteButton != null) {
            holder.deleteButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    // Use the actual item from filteredHistoryList instead of position
                    int currentPosition = holder.getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION && currentPosition < filteredHistoryList.size()) {
                        CalculationHistory itemToDelete = filteredHistoryList.get(currentPosition);
                        actionListener.onDeleteItem(itemToDelete, currentPosition);
                    }
                }
            });
        }
    }

    private void setupStandardListeners(HistoryViewHolder holder, CalculationHistory calculation) {
        // Main item click
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHistoryItemClick(calculation.result);
            }
        });

        // Expression click
        holder.expressionText.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExpressionClick(calculation.expression);
            }
        });

        // Copy button
        holder.copyButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onCopyResult(calculation.result);
            }
        });

        // Action button listeners (for expanded view)
        if (holder.useExpressionButton != null) {
            holder.useExpressionButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onUseExpression(calculation.expression);
                }
            });
        }

        if (holder.deleteButton != null) {
            holder.deleteButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDeleteItem(calculation, holder.getAdapterPosition());
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return filteredHistoryList.size();
    }

    private String getRelativeTimeString(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        } else if (hours < 24) {
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        } else if (days < 7) {
            return days == 1 ? "1 day ago" : days + " days ago";
        } else if (weeks < 4) {
            return weeks == 1 ? "1 week ago" : weeks + " weeks ago";
        } else {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timestamp);
            Calendar now_cal = Calendar.getInstance();

            SimpleDateFormat sdf;
            if (cal.get(Calendar.YEAR) == now_cal.get(Calendar.YEAR)) {
                sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            } else {
                sdf = new SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault());
            }

            return sdf.format(new Date(timestamp));
        }
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView expressionText;
        TextView resultText;
        TextView timestampText;
        ImageButton copyButton;
        LinearLayout actionButtonsContainer;
        LinearLayout quickActionButtons;
        Button useExpressionButton;
        Button deleteButton;
        com.google.android.material.button.MaterialButton quickUseButton;
        com.google.android.material.button.MaterialButton quickDeleteButton;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            expressionText = itemView.findViewById(R.id.tvExpression);
            resultText = itemView.findViewById(R.id.tvResult);
            timestampText = itemView.findViewById(R.id.tvTimestamp);
            copyButton = itemView.findViewById(R.id.btnCopyResult);
            actionButtonsContainer = itemView.findViewById(R.id.actionButtonsContainer);
            quickActionButtons = itemView.findViewById(R.id.quickActionButtons);
            useExpressionButton = itemView.findViewById(R.id.btnUseExpression);
            deleteButton = itemView.findViewById(R.id.btnDeleteItem);
            quickUseButton = itemView.findViewById(R.id.btnQuickUse);
            quickDeleteButton = itemView.findViewById(R.id.btnQuickDelete);
        }
    }
}

// Token class for expression parsing
class Token {
    enum Type { NUMBER, OPERATOR, FUNCTION, PARENTHESIS, CONSTANT }

    Type type;
    String value;
    double numValue;
    int precedence;
    boolean rightAssociative;

    Token(Type type, String value) {
        this.type = type;
        this.value = value;
        this.rightAssociative = false;

        if (type == Type.NUMBER || type == Type.CONSTANT) {
            try {
                this.numValue = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                this.numValue = 0;
            }
        }

        if (type == Type.OPERATOR) {
            switch (value) {
                case "+":
                case "-":
                    this.precedence = 1;
                    break;
                case "*":
                case "/":
                case "×":
                case "÷":
                    this.precedence = 2;
                    break;
                case "^":
                    this.precedence = 3;
                    this.rightAssociative = true;
                    break;
                case "²":
                case "!":
                    this.precedence = 4;
                    break;
                default:
                    this.precedence = 0;
            }
        } else if (type == Type.FUNCTION) {
            this.precedence = 4;
        }
    }
}

public class MainActivity extends AppCompatActivity {

    // UI Components
    private TextView displayText;
    private TextView expressionText;
    private ImageButton historyButton;

    // Calculator state
    private String currentExpression = "";
    private String currentNumber = "";
    private boolean isResultDisplayed = false;
    private boolean isInverseModeActive = false;
    private boolean isDegreeMode = true;
    private boolean hasDecimalPoint = false;

    // Database
    private CalculationDatabase database;
    private ExecutorService executor;

    // Scroll Views for display
    private HorizontalScrollView displayScrollView;
    private HorizontalScrollView expressionScrollView;

    // Auto-scroll state tracking
    private boolean userScrolledDisplay = false;
    private boolean userScrolledExpression = false;

    // Mathematical constants
    private static final double PI = Math.PI;
    private static final double E = Math.E;

    // State preservation keys
    private static final String STATE_CURRENT_EXPRESSION = "current_expression";
    private static final String STATE_CURRENT_NUMBER = "current_number";
    private static final String STATE_IS_RESULT_DISPLAYED = "is_result_displayed";
    private static final String STATE_IS_INVERSE_MODE = "is_inverse_mode";
    private static final String STATE_IS_DEGREE_MODE = "is_degree_mode";
    private static final String STATE_DISPLAY_TEXT = "display_text";
    private static final String STATE_EXPRESSION_TEXT = "expression_text";
    private static final String STATE_HAS_DECIMAL = "has_decimal";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize database and executor
        database = CalculationDatabase.getDatabase(this);
        executor = Executors.newFixedThreadPool(2);

        initializeViews();

        // Restore state if available
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        setupButtonListeners();
        updateFunctionButtonLabels();
        updateDegreeRadianButton();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save calculator state
        outState.putString(STATE_CURRENT_EXPRESSION, currentExpression);
        outState.putString(STATE_CURRENT_NUMBER, currentNumber);
        outState.putBoolean(STATE_IS_RESULT_DISPLAYED, isResultDisplayed);
        outState.putBoolean(STATE_IS_INVERSE_MODE, isInverseModeActive);
        outState.putBoolean(STATE_IS_DEGREE_MODE, isDegreeMode);
        outState.putString(STATE_DISPLAY_TEXT, displayText.getText().toString());
        outState.putString(STATE_EXPRESSION_TEXT, expressionText.getText().toString());
        outState.putBoolean(STATE_HAS_DECIMAL, hasDecimalPoint);
    }

    private void restoreState(Bundle savedInstanceState) {
        currentExpression = savedInstanceState.getString(STATE_CURRENT_EXPRESSION, "");
        currentNumber = savedInstanceState.getString(STATE_CURRENT_NUMBER, "");
        isResultDisplayed = savedInstanceState.getBoolean(STATE_IS_RESULT_DISPLAYED, false);
        isInverseModeActive = savedInstanceState.getBoolean(STATE_IS_INVERSE_MODE, false);
        isDegreeMode = savedInstanceState.getBoolean(STATE_IS_DEGREE_MODE, true);
        hasDecimalPoint = savedInstanceState.getBoolean(STATE_HAS_DECIMAL, false);

        String displayTextValue = savedInstanceState.getString(STATE_DISPLAY_TEXT, "0");
        String expressionTextValue = savedInstanceState.getString(STATE_EXPRESSION_TEXT, "");

        displayText.setText(displayTextValue);
        expressionText.setText(expressionTextValue);

        // Reset scroll states after restoration
        userScrolledDisplay = false;
        userScrolledExpression = false;
    }

    private void initializeViews() {
        displayText = findViewById(R.id.displayText);
        expressionText = findViewById(R.id.expressionText);
        historyButton = findViewById(R.id.btnHistory);
        displayScrollView = findViewById(R.id.displayScrollView);
        expressionScrollView = findViewById(R.id.expressionScrollView);

        // Set initial display only if not restoring state
        if (displayText.getText().toString().isEmpty()) {
            displayText.setText("0");
            expressionText.setText("");
        }

        // Setup scroll listeners
        setupScrollListeners();
    }

    private void setupScrollListeners() {
        // Display scroll listener
        displayScrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                // Check if user manually scrolled (not programmatic scroll)
                if (Math.abs(scrollX - oldScrollX) > 5) {
                    // Get the maximum scroll position (rightmost)
                    int maxScroll = displayScrollView.getChildAt(0).getWidth() - displayScrollView.getWidth();

                    // If user scrolled away from the right edge, mark as user scrolled
                    userScrolledDisplay = scrollX < maxScroll - 20; // 20px tolerance
                }
            }
        });

        // Expression scroll listener
        expressionScrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (Math.abs(scrollX - oldScrollX) > 5) {
                    int maxScroll = expressionScrollView.getChildAt(0).getWidth() - expressionScrollView.getWidth();
                    userScrolledExpression = scrollX < maxScroll - 20;
                }
            }
        });
    }

    private void autoScrollToEnd() {
        // Auto-scroll display to right if user hasn't manually scrolled away
        if (!userScrolledDisplay) {
            displayScrollView.post(new Runnable() {
                @Override
                public void run() {
                    displayScrollView.fullScroll(View.FOCUS_RIGHT);
                }
            });
        }

        // Auto-scroll expression to right if user hasn't manually scrolled away
        if (!userScrolledExpression) {
            expressionScrollView.post(new Runnable() {
                @Override
                public void run() {
                    expressionScrollView.fullScroll(View.FOCUS_RIGHT);
                }
            });
        }
    }

    private void setupButtonListeners() {
        // Number buttons
        setNumberButtonListener(R.id.btn0, "0");
        setNumberButtonListener(R.id.btn1, "1");
        setNumberButtonListener(R.id.btn2, "2");
        setNumberButtonListener(R.id.btn3, "3");
        setNumberButtonListener(R.id.btn4, "4");
        setNumberButtonListener(R.id.btn5, "5");
        setNumberButtonListener(R.id.btn6, "6");
        setNumberButtonListener(R.id.btn7, "7");
        setNumberButtonListener(R.id.btn8, "8");
        setNumberButtonListener(R.id.btn9, "9");

        // Decimal point
        findViewById(R.id.btnDecimal).setOnClickListener(v -> appendDecimal());

        // Basic operations
        setOperatorButtonListener(R.id.btnAdd, "+");
        setOperatorButtonListener(R.id.btnSubtract, "-");
        setOperatorButtonListener(R.id.btnMultiply, "×");
        setOperatorButtonListener(R.id.btnDivide, "÷");

        // Control buttons
        findViewById(R.id.btnClear).setOnClickListener(v -> clearAll());
        findViewById(R.id.btnEquals).setOnClickListener(v -> calculateResult());

        // Percentage button
        findViewById(R.id.btnPercent).setOnClickListener(v -> addPercentage());

        // History button
        historyButton.setOnClickListener(v -> showHistoryModal());

        // Scientific functions (landscape only)
        setupScientificButtons();
    }

    private void setupScientificButtons() {
        Button btnSin = findViewById(R.id.btnSin);
        if (btnSin != null) {
            btnSin.setOnClickListener(v -> addScientificFunction("sin"));
        }

        Button btnCos = findViewById(R.id.btnCos);
        if (btnCos != null) {
            btnCos.setOnClickListener(v -> addScientificFunction("cos"));
        }

        Button btnTan = findViewById(R.id.btnTan);
        if (btnTan != null) {
            btnTan.setOnClickListener(v -> addScientificFunction("tan"));
        }

        Button btnLog = findViewById(R.id.btnLog);
        if (btnLog != null) {
            btnLog.setOnClickListener(v -> addScientificFunction("log"));
        }

        Button btnLn = findViewById(R.id.btnLn);
        if (btnLn != null) {
            btnLn.setOnClickListener(v -> addScientificFunction("ln"));
        }

        Button btnSqrt = findViewById(R.id.btnSqrt);
        if (btnSqrt != null) {
            btnSqrt.setOnClickListener(v -> addScientificFunction("√"));
        }

        Button btnPower = findViewById(R.id.btnPower);
        if (btnPower != null) {
            btnPower.setOnClickListener(v -> addOperator("^"));
        }

        // Constants
        Button btnPi = findViewById(R.id.btnPi);
        if (btnPi != null) {
            btnPi.setOnClickListener(v -> appendConstant(String.valueOf(PI)));
        }

        Button btnE = findViewById(R.id.btnE);
        if (btnE != null) {
            btnE.setOnClickListener(v -> appendConstant(String.valueOf(E)));
        }

        // Parentheses
        Button btnOpenParen = findViewById(R.id.btnOpenParen);
        if (btnOpenParen != null) {
            btnOpenParen.setOnClickListener(v -> appendOperator("("));
        }

        Button btnCloseParen = findViewById(R.id.btnCloseParen);
        if (btnCloseParen != null) {
            btnCloseParen.setOnClickListener(v -> appendOperator(")"));
        }

        // Backspace
        Button btnBackspace = findViewById(R.id.btnBackspace);
        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> backspace());
        }

        // Mode toggles
        Button btnInverse = findViewById(R.id.btnInverse);
        if (btnInverse != null) {
            btnInverse.setOnClickListener(v -> toggleInverseMode());
        }

        Button btnDegRad = findViewById(R.id.btnDegRad);
        if (btnDegRad != null) {
            btnDegRad.setOnClickListener(v -> toggleDegreeRadianMode());
        }

        // Plus/Minus button
        Button btnPlusMinus = findViewById(R.id.btnPlusMinus);
        if (btnPlusMinus != null) {
            btnPlusMinus.setOnClickListener(v -> toggleSign());
        }

        Button btnFactorial = findViewById(R.id.btnFactorial);
        if (btnFactorial != null) {
            btnFactorial.setOnClickListener(v -> addFactorial());
        }
    }

    private void setNumberButtonListener(int buttonId, String number) {
        findViewById(buttonId).setOnClickListener(v -> appendNumber(number));
    }

    private void setOperatorButtonListener(int buttonId, String operator) {
        findViewById(buttonId).setOnClickListener(v -> addOperator(operator));
    }

    private void appendNumber(String number) {
        if (isResultDisplayed) {
            clearAll();
            isResultDisplayed = false;
        }

        if (currentNumber.equals("0") && number.equals("0")) {
            return;
        }

        if (currentNumber.equals("0") && !number.equals("0")) {
            currentNumber = number;
        } else {
            currentNumber += number;
        }

        updateDisplay();
    }

    private void appendDecimal() {
        if (isResultDisplayed) {
            clearAll();
            isResultDisplayed = false;
        }

        if (!hasDecimalPoint) {
            if (currentNumber.isEmpty()) {
                currentNumber = "0.";
            } else {
                currentNumber += ".";
            }
            hasDecimalPoint = true;
            updateDisplay();
        }
    }

    private void appendConstant(String constant) {
        if (isResultDisplayed) {
            clearAll();
            isResultDisplayed = false;
        }

        if (!currentNumber.isEmpty()) {
            currentExpression += currentNumber;
            currentExpression += "*";
            currentNumber = "";
            hasDecimalPoint = false;
        }

        currentNumber = constant;
        hasDecimalPoint = true;
        updateDisplay();
    }

    private void addOperator(String operator) {
        if (isResultDisplayed) {
            currentExpression = displayText.getText().toString();
            currentNumber = "";
            hasDecimalPoint = false;
            isResultDisplayed = false;
        }

        if (!currentNumber.isEmpty()) {
            currentExpression += currentNumber;
            currentNumber = "";
            hasDecimalPoint = false;
        }

        if (!currentExpression.isEmpty() && isOperator(currentExpression.substring(currentExpression.length() - 1))) {
            if (operator.equals("-") && !currentExpression.endsWith("-")) {
                currentExpression += operator;
            } else {
                currentExpression = currentExpression.substring(0, currentExpression.length() - 1) + operator;
            }
        } else {
            currentExpression += operator;
        }

        updateDisplay();
    }

    private void appendOperator(String operator) {
        if (isResultDisplayed) {
            if (operator.equals("(")) {
                clearAll();
            } else {
                currentExpression = displayText.getText().toString();
                currentNumber = "";
                hasDecimalPoint = false;
                isResultDisplayed = false;
            }
        }

        if (!currentNumber.isEmpty() && !operator.equals("(")) {
            currentExpression += currentNumber;
            currentNumber = "";
            hasDecimalPoint = false;
        }

        currentExpression += operator;
        updateDisplay();
    }

    private void addScientificFunction(String function) {
        if (isResultDisplayed) {
            clearAll();
            isResultDisplayed = false;
        }

        String actualFunction = function;
        if (isInverseModeActive) {
            switch (function) {
                case "sin": actualFunction = "asin"; break;
                case "cos": actualFunction = "acos"; break;
                case "tan": actualFunction = "atan"; break;
                case "log": actualFunction = "10^"; break;
                case "ln": actualFunction = "e^"; break;
                case "√": actualFunction = "x²"; break;
            }
        }

        if (!currentNumber.isEmpty()) {
            currentExpression += currentNumber;
            currentNumber = "";
            hasDecimalPoint = false;
        }

        if (actualFunction.equals("x²")) {
            currentExpression += "²";
        } else if (actualFunction.equals("10^") || actualFunction.equals("e^")) {
            currentExpression = actualFunction + "(" + currentExpression + ")";
        } else {
            currentExpression += actualFunction + "(";
        }

        updateDisplay();
    }

    private void addPercentage() {
        if (!currentNumber.isEmpty()) {
            try {
                double value = Double.parseDouble(currentNumber);
                double percentage = value / 100.0;
                currentNumber = formatResult(percentage);
                hasDecimalPoint = currentNumber.contains(".");
                updateDisplay();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number for percentage", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addFactorial() {
        if (isResultDisplayed) {
            // If showing a result, apply factorial to that result
            String displayValue = displayText.getText().toString();
            try {
                double value = Double.parseDouble(displayValue);
                if (value < 0 || value != Math.floor(value) || value > 170) {
                    Toast.makeText(this, "Factorial only works with non-negative integers ≤ 170", Toast.LENGTH_SHORT).show();
                    return;
                }
                double result = factorial((int) value);
                String resultStr = formatResult(result);

                saveCalculationToHistory(displayValue + "!", resultStr);
                displayText.setText(resultStr);
                expressionText.setText(displayValue + "! =");
                currentExpression = "";
                currentNumber = "";
                hasDecimalPoint = resultStr.contains(".");
                return;
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number for factorial", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (!currentNumber.isEmpty()) {
            // Apply factorial to current number
            try {
                double value = Double.parseDouble(currentNumber);
                if (value < 0 || value != Math.floor(value) || value > 170) {
                    Toast.makeText(this, "Factorial only works with non-negative integers ≤ 170", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentNumber = currentNumber + "!";
                updateDisplay();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number for factorial", Toast.LENGTH_SHORT).show();
            }
        } else if (!currentExpression.isEmpty()) {
            // Add factorial to the last operand in expression
            currentExpression += "!";
            updateDisplay();
        }
    }

    private double factorial(int n) {
        if (n < 0) return Double.NaN;
        if (n == 0 || n == 1) return 1;
        if (n > 170) return Double.POSITIVE_INFINITY; // Prevent overflow

        double result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private void clearAll() {
        currentExpression = "";
        currentNumber = "";
        hasDecimalPoint = false;
        displayText.setText("0");
        expressionText.setText("");
        isResultDisplayed = false;

        // Reset scroll states and position properly
        userScrolledDisplay = false;
        userScrolledExpression = false;

        // Reset scroll position to show "0" properly aligned to right
        displayScrollView.post(new Runnable() {
            @Override
            public void run() {
                displayScrollView.scrollTo(0, 0);
            }
        });

        expressionScrollView.post(new Runnable() {
            @Override
            public void run() {
                expressionScrollView.scrollTo(0, 0);
            }
        });
    }

    private void backspace() {
        if (isResultDisplayed) {
            clearAll();
            return;
        }

        if (!currentNumber.isEmpty()) {
            String lastChar = currentNumber.substring(currentNumber.length() - 1);
            if (lastChar.equals(".")) {
                hasDecimalPoint = false;
            }
            currentNumber = currentNumber.substring(0, currentNumber.length() - 1);

            if (currentNumber.isEmpty()) {
                displayText.setText("0");
                return;
            }
        } else if (!currentExpression.isEmpty()) {
            currentExpression = currentExpression.substring(0, currentExpression.length() - 1);
        }
        updateDisplay();
    }

    private void calculateResult() {
        String fullExpression = currentExpression + currentNumber;
        if (fullExpression.isEmpty() || fullExpression.trim().isEmpty()) {
            if (!currentNumber.isEmpty()) {
                displayText.setText(currentNumber);
                expressionText.setText(currentNumber + " =");
                isResultDisplayed = true;
            }
            return;
        }

        try {
            double result = evaluateExpression(fullExpression);
            String resultStr = formatResult(result);

            saveCalculationToHistory(fullExpression, resultStr);

            displayText.setText(resultStr);
            expressionText.setText(fullExpression + " =");

            currentExpression = "";
            currentNumber = "";
            hasDecimalPoint = false;
            isResultDisplayed = true;

        } catch (Exception e) {
            displayText.setText("Error");
            expressionText.setText("Invalid expression");
            Toast.makeText(this, "Calculation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatResult(double result) {
        if (Double.isNaN(result)) {
            return "NaN";
        }
        if (Double.isInfinite(result)) {
            return result > 0 ? "∞" : "-∞";
        }

        BigDecimal bd = new BigDecimal(result);
        bd = bd.round(new MathContext(15, RoundingMode.HALF_UP));
        bd = bd.stripTrailingZeros();

        if (bd.scale() <= 0 && bd.abs().compareTo(new BigDecimal("999999999999999")) <= 0) {
            return bd.toPlainString();
        } else if (bd.scale() > 0 && bd.abs().compareTo(new BigDecimal("0.000001")) >= 0
                && bd.abs().compareTo(new BigDecimal("999999999999999")) <= 0) {
            return bd.toPlainString();
        } else {
            DecimalFormat df = new DecimalFormat("0.##########E0");
            return df.format(result);
        }
    }

    private void updateDisplay() {
        String display = currentExpression + currentNumber;
        if (display.isEmpty()) {
            displayText.setText("0");
        } else {
            displayText.setText(display);
        }
        expressionText.setText(currentExpression);

        // Auto-scroll to show newest input
        autoScrollToEnd();
    }

    private void toggleInverseMode() {
        isInverseModeActive = !isInverseModeActive;
        updateFunctionButtonLabels();
    }

    private void updateFunctionButtonLabels() {
        Button inverseBtn = findViewById(R.id.btnInverse);
        Button sinBtn = findViewById(R.id.btnSin);
        Button cosBtn = findViewById(R.id.btnCos);
        Button tanBtn = findViewById(R.id.btnTan);
        Button logBtn = findViewById(R.id.btnLog);
        Button lnBtn = findViewById(R.id.btnLn);
        Button sqrtBtn = findViewById(R.id.btnSqrt);

        if (inverseBtn != null) {
            inverseBtn.setText(isInverseModeActive ? "INV" : "2nd");
        }

        if (sinBtn != null) {
            sinBtn.setText(isInverseModeActive ? "asin" : "sin");
        }

        if (cosBtn != null) {
            cosBtn.setText(isInverseModeActive ? "acos" : "cos");
        }

        if (tanBtn != null) {
            tanBtn.setText(isInverseModeActive ? "atan" : "tan");
        }

        if (logBtn != null) {
            logBtn.setText(isInverseModeActive ? "10^x" : "log");
        }

        if (lnBtn != null) {
            lnBtn.setText(isInverseModeActive ? "e^x" : "ln");
        }

        if (sqrtBtn != null) {
            sqrtBtn.setText(isInverseModeActive ? "x²" : "√");
        }
    }

    private void toggleDegreeRadianMode() {
        isDegreeMode = !isDegreeMode;
        updateDegreeRadianButton();
    }

    private void updateDegreeRadianButton() {
        Button degRadBtn = findViewById(R.id.btnDegRad);
        if (degRadBtn != null) {
            degRadBtn.setText(isDegreeMode ? "DEG" : "RAD");
        }
    }

    private void toggleSign() {
        if (isResultDisplayed) {
            String displayValue = displayText.getText().toString();
            try {
                double value = Double.parseDouble(displayValue);
                double toggledValue = -value;
                String toggledStr = formatResult(toggledValue);
                displayText.setText(toggledStr);
                currentNumber = toggledStr;
                hasDecimalPoint = toggledStr.contains(".");
            } catch (NumberFormatException e) {
                if (displayValue.equals("∞")) {
                    displayText.setText("-∞");
                    currentNumber = "-∞";
                } else if (displayValue.equals("-∞")) {
                    displayText.setText("∞");
                    currentNumber = "∞";
                }
            }
        } else if (!currentNumber.isEmpty()) {
            if (currentNumber.startsWith("-")) {
                currentNumber = currentNumber.substring(1);
            } else {
                currentNumber = "-" + currentNumber;
            }
            updateDisplay();
        } else {
            currentNumber = "-";
            updateDisplay();
        }
    }

    private void showHistoryModal() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean isTablet = (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;

        Dialog historyDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        historyDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        historyDialog.setContentView(R.layout.history_modal);

        Window window = historyDialog.getWindow();
        if (window != null) {
            window.setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.setBackgroundBlurRadius(isLandscape ? 15 : 20);
            } else {
                window.setDimAmount(isLandscape ? 0.5f : 0.6f);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }

            if (isLandscape) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowCompat.setDecorFitsSystemWindows(window, false);
                    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
                    if (controller != null) {
                        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                        boolean isNightMode = (getResources().getConfiguration().uiMode &
                                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                        controller.setAppearanceLightStatusBars(!isNightMode);
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowCompat.setDecorFitsSystemWindows(window, false);
                    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
                    if (controller != null) {
                        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                        boolean isNightMode = (getResources().getConfiguration().uiMode &
                                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                        controller.setAppearanceLightStatusBars(!isNightMode);
                    }
                }
            }

            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.transparent));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    window.setNavigationBarColor(ContextCompat.getColor(this, android.R.color.transparent));
                }
            }
        }

        handleStatusBarSpacer(historyDialog, isLandscape);

        ImageButton closeButton = historyDialog.findViewById(R.id.btnCloseHistory);
        LinearLayout emptyStateLayout = historyDialog.findViewById(R.id.emptyStateLayout);
        androidx.constraintlayout.widget.ConstraintLayout historyContentLayout =
                historyDialog.findViewById(R.id.historyContentLayout);
        LinearLayout actionButtonsLayout = historyDialog.findViewById(R.id.actionButtonsLayout);

        TextInputEditText searchHistoryHidden = historyDialog.findViewById(R.id.searchHistory);
        TextInputEditText searchHistoryVisible = historyDialog.findViewById(R.id.searchHistoryVisible);

        RecyclerView historyRecyclerView = historyDialog.findViewById(R.id.historyRecyclerView);
        Button clearAllButton = historyDialog.findViewById(R.id.btnClearAllHistory);
        Button exportButton = historyDialog.findViewById(R.id.btnExportHistory);

        setupRecyclerView(historyRecyclerView, isLandscape, isTablet);

        HistoryAdapter historyAdapter = new HistoryAdapter();
        historyAdapter.setLandscapeMode(false);
        historyRecyclerView.setAdapter(historyAdapter);

        setupHistoryItemListeners(historyAdapter, historyDialog);
        setupSearchFunctionality(searchHistoryHidden, searchHistoryVisible, historyAdapter);
        loadHistoryData(historyAdapter, emptyStateLayout, historyContentLayout, actionButtonsLayout);
        setupModalButtons(historyDialog, closeButton, clearAllButton, exportButton,
                historyAdapter, emptyStateLayout, historyContentLayout, actionButtonsLayout);

        showModalWithAnimation(historyDialog, isLandscape);
    }

    private void handleStatusBarSpacer(Dialog dialog, boolean isLandscape) {
        View statusBarSpacer = dialog.findViewById(R.id.statusBarSpacer);
        if (statusBarSpacer != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dialog.getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                    WindowInsetsCompat windowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
                    int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

                    int actualHeight = isLandscape ? Math.min(statusBarHeight, dpToPx(16)) : statusBarHeight;

                    ViewGroup.LayoutParams params = statusBarSpacer.getLayoutParams();
                    params.height = actualHeight;
                    statusBarSpacer.setLayoutParams(params);

                    return insets;
                });
            } else {
                int statusBarHeight = getStatusBarHeight();
                int actualHeight = isLandscape ? Math.min(statusBarHeight, dpToPx(16)) : statusBarHeight;

                ViewGroup.LayoutParams params = statusBarSpacer.getLayoutParams();
                params.height = actualHeight;
                statusBarSpacer.setLayoutParams(params);
            }
        }
    }

    private void setupRecyclerView(RecyclerView recyclerView, boolean isLandscape, boolean isTablet) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setNestedScrollingEnabled(true);
        recyclerView.setHasFixedSize(true);

        androidx.recyclerview.widget.DefaultItemAnimator animator = new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setChangeDuration(150);
        animator.setMoveDuration(150);
        recyclerView.setItemAnimator(animator);

        if (isLandscape) {
            int spacing = isTablet ? dpToPx(4) : dpToPx(2);
            recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    outRect.bottom = spacing;
                }
            });
        }

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });
    }

    private void setupHistoryItemListeners(HistoryAdapter adapter, Dialog dialog) {
        // In setupHistoryItemListeners method, replace the onDeleteItem implementation:
        adapter.setOnHistoryItemActionListener(new HistoryAdapter.OnHistoryItemActionListener() {
            @Override
            public void onCopyResult(String result) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Calculator Result", result);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteItem(CalculationHistory item, int position) {
                // Prevent duplicate deletions
                if (adapter.isDeletionPending(item.id)) {
                    return;
                }

                // Mark this deletion as pending
                adapter.markDeletionPending(item.id);

                // Disable the button temporarily to prevent rapid clicks
                executor.execute(() -> {
                    database.calculationHistoryDao().deleteById(item.id);
                    runOnUiThread(() -> {
                        // Mark deletion complete
                        adapter.markDeletionComplete(item.id);

                        // Find the current position of this item in the filtered list
                        int currentPos = -1;
                        for (int i = 0; i < adapter.getFilteredHistoryList().size(); i++) {
                            if (adapter.getFilteredHistoryList().get(i).id == item.id) {
                                currentPos = i;
                                break;
                            }
                        }

                        if (currentPos != -1) {
                            adapter.removeItem(currentPos);
                            Toast.makeText(MainActivity.this, "Item deleted", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }

            @Override
            public void onUseExpression(String expression) {
                currentExpression = expression;
                currentNumber = "";
                hasDecimalPoint = false;
                isResultDisplayed = false;
                updateDisplay();
                dismissModalWithAnimation(dialog);
            }
        });
    }

    private void setupSearchFunctionality(TextInputEditText searchHidden,
                                          TextInputEditText searchVisible,
                                          HistoryAdapter adapter) {

        android.os.Handler searchHandler = new android.os.Handler();

        TextWatcher searchWatcher = new TextWatcher() {
            private Runnable searchRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                searchRunnable = () -> adapter.filter(s.toString());
                searchHandler.postDelayed(searchRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (searchHidden != null) {
            searchHidden.addTextChangedListener(searchWatcher);
        }
        if (searchVisible != null) {
            searchVisible.addTextChangedListener(searchWatcher);

            searchVisible.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (searchHidden != null && !searchHidden.getText().toString().equals(s.toString())) {
                        searchHidden.setText(s);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void loadHistoryData(HistoryAdapter adapter, LinearLayout emptyStateLayout,
                                 androidx.constraintlayout.widget.ConstraintLayout historyContentLayout,
                                 LinearLayout actionButtonsLayout) {
        executor.execute(() -> {
            List<CalculationHistory> history = database.calculationHistoryDao().getRecentCalculations();
            runOnUiThread(() -> {
                if (history.isEmpty()) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    historyContentLayout.setVisibility(View.GONE);
                    actionButtonsLayout.setVisibility(View.GONE);
                } else {
                    emptyStateLayout.setVisibility(View.GONE);
                    historyContentLayout.setVisibility(View.VISIBLE);
                    actionButtonsLayout.setVisibility(View.VISIBLE);
                    adapter.setHistoryList(history);
                }
            });
        });
    }

    private void setupModalButtons(Dialog dialog, ImageButton closeButton,
                                   Button clearAllButton, Button exportButton,
                                   HistoryAdapter adapter, LinearLayout emptyStateLayout,
                                   androidx.constraintlayout.widget.ConstraintLayout historyContentLayout,
                                   LinearLayout actionButtonsLayout) {

        closeButton.setOnClickListener(v -> dismissModalWithAnimation(dialog));

        clearAllButton.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Clear History")
                    .setMessage("Are you sure you want to clear all calculation history?")
                    .setPositiveButton("Clear", (d, which) -> {
                        executor.execute(() -> {
                            database.calculationHistoryDao().clearHistory();
                            runOnUiThread(() -> {
                                adapter.setHistoryList(new ArrayList<>());
                                emptyStateLayout.setVisibility(View.VISIBLE);
                                historyContentLayout.setVisibility(View.GONE);
                                actionButtonsLayout.setVisibility(View.GONE);
                                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                            });
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        exportButton.setOnClickListener(v -> exportHistory());
    }

    private void exportHistory() {
        executor.execute(() -> {
            List<CalculationHistory> allHistory = database.calculationHistoryDao().getAllCalculations();
            StringBuilder exportText = new StringBuilder();
            exportText.append("Calculator History Export\n");
            exportText.append("========================\n\n");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            for (CalculationHistory calc : allHistory) {
                exportText.append("Expression: ").append(calc.expression).append("\n");
                exportText.append("Result: ").append(calc.result).append("\n");
                exportText.append("Date: ").append(sdf.format(new Date(calc.timestamp))).append("\n");
                exportText.append("---\n");
            }

            runOnUiThread(() -> {
                android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, exportText.toString());
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Calculator History");
                startActivity(android.content.Intent.createChooser(shareIntent, "Export History"));
            });
        });
    }

    private void showModalWithAnimation(Dialog dialog, boolean isLandscape) {
        dialog.show();

        if (dialog.getWindow() != null) {
            View decorView = dialog.getWindow().getDecorView();
            decorView.setAlpha(0f);

            int duration = isLandscape ? 200 : 250;

            decorView.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void dismissModalWithAnimation(Dialog dialog) {
        if (dialog.getWindow() != null) {
            View decorView = dialog.getWindow().getDecorView();

            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int duration = isLandscape ? 150 : 200;

            decorView.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(dialog::dismiss)
                    .start();
        } else {
            dialog.dismiss();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void saveCalculationToHistory(String expression, String result) {
        executor.execute(() -> {
            CalculationHistory calculation = new CalculationHistory(
                    expression, result, System.currentTimeMillis()
            );
            database.calculationHistoryDao().insert(calculation);
        });
    }

    // FIXED EXPRESSION EVALUATOR - This is where the bug was!
    private double evaluateExpression(String expression) throws Exception {
        expression = expression.trim();
        if (expression.isEmpty()) {
            throw new Exception("Empty expression");
        }

        // Replace display symbols with standard ones
        expression = expression.replace("×", "*").replace("÷", "/");

        // Tokenize and evaluate using Shunting Yard algorithm
        return evaluateWithShuntingYard(expression);
    }

    private List<Token> tokenize(String expression) throws Exception {
        List<Token> tokens = new ArrayList<>();
        StringBuilder numberBuffer = new StringBuilder();
        boolean lastWasOperatorOrFunction = true; // Consider start as operator position for unary minus

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                numberBuffer.append(c);
                lastWasOperatorOrFunction = false;
            } else if (c == ' ') {
                continue; // Skip spaces
            } else {
                // Process accumulated number
                if (numberBuffer.length() > 0) {
                    tokens.add(new Token(Token.Type.NUMBER, numberBuffer.toString()));
                    numberBuffer.setLength(0);
                    lastWasOperatorOrFunction = false;
                }

                // Handle constants
                if (c == 'π' || (i < expression.length() - 1 && expression.substring(i, i + 2).equals("pi"))) {
                    tokens.add(new Token(Token.Type.CONSTANT, String.valueOf(PI)));
                    if (c != 'π') i++; // Skip the 'i' in "pi"
                    lastWasOperatorOrFunction = false;
                } else if (c == 'e' && (i == expression.length() - 1 || !Character.isLetter(expression.charAt(i + 1)))) {
                    tokens.add(new Token(Token.Type.CONSTANT, String.valueOf(E)));
                    lastWasOperatorOrFunction = false;
                } else if (c == '(' || c == ')') {
                    tokens.add(new Token(Token.Type.PARENTHESIS, String.valueOf(c)));
                    lastWasOperatorOrFunction = (c == '(');
                } else if (c == '+' || c == '*' || c == '/' || c == '^') {
                    tokens.add(new Token(Token.Type.OPERATOR, String.valueOf(c)));
                    lastWasOperatorOrFunction = true;
                } else if (c == '-') {
                    if (lastWasOperatorOrFunction) {
                        // This is a unary minus
                        tokens.add(new Token(Token.Type.NUMBER, "0"));
                        tokens.add(new Token(Token.Type.OPERATOR, "-"));
                    } else {
                        tokens.add(new Token(Token.Type.OPERATOR, "-"));
                    }
                    lastWasOperatorOrFunction = true;
                } else if (c == '²') {
                    tokens.add(new Token(Token.Type.OPERATOR, "²"));
                    lastWasOperatorOrFunction = false;
                } else if (c == '!') {
                    tokens.add(new Token(Token.Type.OPERATOR, "!"));
                    lastWasOperatorOrFunction = false;
                } else if (c == '√') {
                    tokens.add(new Token(Token.Type.FUNCTION, "sqrt"));
                    lastWasOperatorOrFunction = true;
                } else if (isStartOfFunction(expression, i)) {
                    String function = extractFunction(expression, i);
                    tokens.add(new Token(Token.Type.FUNCTION, function));
                    i += function.length() - 1; // Skip function characters
                    lastWasOperatorOrFunction = true;
                }
            }
        }

        // Process final number
        if (numberBuffer.length() > 0) {
            tokens.add(new Token(Token.Type.NUMBER, numberBuffer.toString()));
        }

        return tokens;
    }

    private boolean isStartOfFunction(String expression, int index) {
        String[] functions = {"sin", "cos", "tan", "log", "ln", "asin", "acos", "atan", "sqrt"};
        for (String func : functions) {
            if (expression.substring(index).toLowerCase().startsWith(func)) {
                return true;
            }
        }
        return false;
    }

    private String extractFunction(String expression, int index) {
        String[] functions = {"asin", "acos", "atan", "sin", "cos", "tan", "log", "ln", "sqrt"};
        for (String func : functions) {
            if (expression.substring(index).toLowerCase().startsWith(func)) {
                return func;
            }
        }
        return "";
    }

    private double evaluateWithShuntingYard(String expression) throws Exception {
        List<Token> tokens = tokenize(expression);
        Stack<Token> operators = new Stack<>();
        Stack<Double> operands = new Stack<>();

        for (Token token : tokens) {
            switch (token.type) {
                case NUMBER:
                case CONSTANT:
                    operands.push(token.numValue);
                    break;

                case FUNCTION:
                    operators.push(token);
                    break;

                case OPERATOR:
                    // Handle unary operators differently
                    if (token.value.equals("²") || token.value.equals("!")) {
                        // Process immediately for postfix operators
                        processOperator(token, operands);
                    } else {
                        while (!operators.empty() &&
                                operators.peek().type != Token.Type.PARENTHESIS &&
                                (operators.peek().precedence > token.precedence ||
                                        (operators.peek().precedence == token.precedence && !token.rightAssociative))) {
                            processOperator(operators.pop(), operands);
                        }
                        operators.push(token);
                    }
                    break;

                case PARENTHESIS:
                    if (token.value.equals("(")) {
                        operators.push(token);
                    } else { // ")"
                        while (!operators.empty() && !operators.peek().value.equals("(")) {
                            processOperator(operators.pop(), operands);
                        }
                        if (operators.empty()) {
                            throw new Exception("Mismatched parentheses");
                        }
                        operators.pop(); // Remove the "("

                        // Process function if present
                        if (!operators.empty() && operators.peek().type == Token.Type.FUNCTION) {
                            processOperator(operators.pop(), operands);
                        }
                    }
                    break;
            }
        }

        // Process remaining operators
        while (!operators.empty()) {
            if (operators.peek().type == Token.Type.PARENTHESIS) {
                throw new Exception("Mismatched parentheses");
            }
            processOperator(operators.pop(), operands);
        }

        if (operands.size() != 1) {
            throw new Exception("Invalid expression");
        }

        return operands.pop();
    }

    private void processOperator(Token operator, Stack<Double> operands) throws Exception {
        if (operator.type == Token.Type.FUNCTION) {
            if (operands.empty()) {
                throw new Exception("Missing operand for function " + operator.value);
            }
            double operand = operands.pop();
            double result = applyFunction(operator.value, operand);
            operands.push(result);
        } else if (operator.type == Token.Type.OPERATOR) {
            if (operator.value.equals("²")) {
                if (operands.empty()) {
                    throw new Exception("Missing operand for square operation");
                }
                double operand = operands.pop();
                operands.push(operand * operand);
            } else if (operator.value.equals("!")) {
                if (operands.empty()) {
                    throw new Exception("Missing operand for factorial operation");
                }
                double operand = operands.pop();
                if (operand < 0 || operand != Math.floor(operand)) {
                    throw new Exception("Factorial only works with non-negative integers");
                }
                if (operand > 170) {
                    throw new Exception("Factorial argument too large (max 170)");
                }
                operands.push(factorial((int) operand));
            } else {
                if (operands.size() < 2) {
                    throw new Exception("Missing operand for operator " + operator.value);
                }
                double right = operands.pop();
                double left = operands.pop();
                double result = applyBinaryOperator(operator.value, left, right);
                operands.push(result);
            }
        }
    }

    private double applyFunction(String function, double operand) throws Exception {
        switch (function.toLowerCase()) {
            case "sin":
                return Math.sin(isDegreeMode ? Math.toRadians(operand) : operand);
            case "cos":
                return Math.cos(isDegreeMode ? Math.toRadians(operand) : operand);
            case "tan":
                return Math.tan(isDegreeMode ? Math.toRadians(operand) : operand);
            case "asin":
                double asinResult = Math.asin(operand);
                return isDegreeMode ? Math.toDegrees(asinResult) : asinResult;
            case "acos":
                double acosResult = Math.acos(operand);
                return isDegreeMode ? Math.toDegrees(acosResult) : acosResult;
            case "atan":
                double atanResult = Math.atan(operand);
                return isDegreeMode ? Math.toDegrees(atanResult) : atanResult;
            case "log":
                if (operand <= 0) throw new Exception("Log of non-positive number");
                return Math.log10(operand);
            case "ln":
                if (operand <= 0) throw new Exception("Ln of non-positive number");
                return Math.log(operand);
            case "sqrt":
                if (operand < 0) throw new Exception("Square root of negative number");
                return Math.sqrt(operand);
            default:
                throw new Exception("Unknown function: " + function);
        }
    }

    private double applyBinaryOperator(String operator, double left, double right) throws Exception {
        switch (operator) {
            case "+":
                return left + right;
            case "-":
                return left - right;
            case "*":
                return left * right;
            case "/":
                if (Math.abs(right) < 1e-15) {
                    throw new Exception("Division by zero");
                }
                return left / right;
            case "^":
                // Handle special cases for power operations
                if (left == 0 && right < 0) {
                    throw new Exception("0 to negative power is undefined");
                }
                if (left < 0 && right != Math.floor(right)) {
                    throw new Exception("Complex result: negative base with non-integer exponent");
                }
                return Math.pow(left, right);
            default:
                throw new Exception("Unknown operator: " + operator);
        }
    }

    private boolean isOperator(String token) {
        return token.equals("+") || token.equals("-") || token.equals("×") ||
                token.equals("÷") || token.equals("*") || token.equals("/") ||
                token.equals("^") || token.equals("!") || token.equals("²");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}