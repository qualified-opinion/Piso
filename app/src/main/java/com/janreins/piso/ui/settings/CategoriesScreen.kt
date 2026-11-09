package com.janreins.piso.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoTopBar
import com.janreins.piso.ui.theme.ExpenseContainer
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.IncomeContainer
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.ui.theme.OnTealPrimary

@Composable
fun CategoriesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories = uiState.categories
    val subcategories = uiState.subcategories

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Expense, 1: Income
    val currentKind = if (selectedTabIndex == 0) "EXPENSE" else "INCOME"

    val filteredCategories = remember(categories, currentKind) {
        categories.filter { it.kind.equals(currentKind, ignoreCase = true) }
    }

    // Expanded category IDs for viewing subcategories
    var expandedCategoryNames by remember { mutableStateOf(setOf<String>()) }

    // Dialog state
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<UserCategory?>(null) }
    var parentForNewSubcategory by remember { mutableStateOf<UserCategory?>(null) }
    var subcategoryToRename by remember { mutableStateOf<UserSubcategory?>(null) }

    Scaffold(
        modifier = modifier.testTag("categories_screen"),
        topBar = {
            PisoTopBar(
                title = "Categories",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("categories_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddCategoryDialog = true },
                        modifier = Modifier.testTag("add_category_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Category",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Segmented Tab Row: Expense / Income
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = TealPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = TealPrimary
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        val expCount = categories.count { it.kind.equals("EXPENSE", ignoreCase = true) }
                        Text(
                            text = "Expense ($expCount)",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == 0) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("categories_tab_expense")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        val incCount = categories.count { it.kind.equals("INCOME", ignoreCase = true) }
                        Text(
                            text = "Income ($incCount)",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == 1) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("categories_tab_income")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add Category Action Bar
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Button(
                    onClick = { showAddCategoryDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("add_category_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTabIndex == 0) ExpenseContainer else IncomeContainer,
                        contentColor = if (selectedTabIndex == 0) ExpenseRed else IncomeGreen
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedTabIndex == 0) "Add Expense Category" else "Add Income Category",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of Categories
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCategories, key = { it.id }) { category ->
                    val isExpanded = expandedCategoryNames.contains(category.name)
                    val catSubcategories = remember(subcategories, category.name, category.kind) {
                        subcategories.filter {
                            it.parentCategoryName.equals(category.name, ignoreCase = true) &&
                                it.parentKind.equals(category.kind, ignoreCase = true)
                        }
                    }

                    CategoryListItem(
                        category = category,
                        subcategories = catSubcategories,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedCategoryNames = if (isExpanded) {
                                expandedCategoryNames - category.name
                            } else {
                                expandedCategoryNames + category.name
                            }
                        },
                        onRenameCategory = { categoryToRename = category },
                        onToggleArchiveCategory = { viewModel.toggleCategoryArchived(category) },
                        onAddSubcategory = { parentForNewSubcategory = category },
                        onRenameSubcategory = { subcategoryToRename = it },
                        onToggleArchiveSubcategory = { viewModel.toggleSubcategoryArchived(it) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    // --- Dialog 1: Add Category ---
    if (showAddCategoryDialog) {
        CategoryInputDialog(
            title = if (selectedTabIndex == 0) "Add Expense Category" else "Add Income Category",
            initialName = "",
            confirmText = "Add",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                viewModel.addCategory(name, currentKind) { success, errorMsg ->
                    if (success) {
                        showAddCategoryDialog = false
                    } else if (errorMsg != null) {
                        viewModel.showMessage(errorMsg)
                    }
                }
            }
        )
    }

    // --- Dialog 2: Rename Category ---
    categoryToRename?.let { cat ->
        CategoryInputDialog(
            title = "Rename category",
            initialName = cat.name,
            confirmText = "Save",
            onDismiss = { categoryToRename = null },
            onConfirm = { newName ->
                viewModel.updateCategoryName(cat, newName) { success, errorMsg ->
                    if (success) {
                        categoryToRename = null
                    } else if (errorMsg != null) {
                        viewModel.showMessage(errorMsg)
                    }
                }
            }
        )
    }

    // --- Dialog 3: Add Subcategory ---
    parentForNewSubcategory?.let { parentCat ->
        CategoryInputDialog(
            title = "Add Subcategory under ${parentCat.name}",
            initialName = "",
            confirmText = "Add",
            placeholder = "e.g. Groceries, Dining out",
            onDismiss = { parentForNewSubcategory = null },
            onConfirm = { subName ->
                viewModel.addSubcategory(parentCat.name, parentCat.kind, subName) { success, errorMsg ->
                    if (success) {
                        // Ensure parent is expanded so user sees new subcategory
                        expandedCategoryNames = expandedCategoryNames + parentCat.name
                        parentForNewSubcategory = null
                    } else if (errorMsg != null) {
                        viewModel.showMessage(errorMsg)
                    }
                }
            }
        )
    }

    // --- Dialog 4: Rename Subcategory ---
    subcategoryToRename?.let { subcat ->
        CategoryInputDialog(
            title = "Rename subcategory",
            initialName = subcat.name,
            confirmText = "Save",
            onDismiss = { subcategoryToRename = null },
            onConfirm = { newName ->
                viewModel.updateSubcategoryName(subcat, newName) { success, errorMsg ->
                    if (success) {
                        subcategoryToRename = null
                    } else if (errorMsg != null) {
                        viewModel.showMessage(errorMsg)
                    }
                }
            }
        )
    }
}

@Composable
private fun CategoryListItem(
    category: UserCategory,
    subcategories: List<UserSubcategory>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRenameCategory: () -> Unit,
    onToggleArchiveCategory: () -> Unit,
    onAddSubcategory: () -> Unit,
    onRenameSubcategory: (UserSubcategory) -> Unit,
    onToggleArchiveSubcategory: (UserSubcategory) -> Unit
) {
    val activeSubCount = subcategories.count { !it.isArchived }

    PisoCard(
        contentPadding = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Parent Category Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (category.isArchived) MaterialTheme.colorScheme.surfaceVariant
                                else if (category.kind == "EXPENSE") ExpenseContainer
                                else IncomeContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (category.isArchived) MaterialTheme.colorScheme.outline
                            else if (category.kind == "EXPENSE") ExpenseRed
                            else IncomeGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = if (category.isArchived) FontStyle.Italic else FontStyle.Normal
                                ),
                                color = if (category.isArchived) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            if (category.isArchived) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Archived",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (subcategories.isEmpty()) "Tap to add subcategories"
                            else "$activeSubCount subcategor${if (activeSubCount == 1) "y" else "ies"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons on parent category
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRenameCategory,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleArchiveCategory,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (category.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (category.isArchived) "Unarchive" else "Archive",
                            tint = if (category.isArchived) TealPrimary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded Subcategories Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (subcategories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No subcategories yet – add Groceries under ${category.name} if you want.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            subcategories.forEach { sub ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "• ${sub.name}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontStyle = if (sub.isArchived) FontStyle.Italic else FontStyle.Normal
                                            ),
                                            color = if (sub.isArchived) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (sub.isArchived) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "(archived)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onRenameSubcategory(sub) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename subcategory",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onToggleArchiveSubcategory(sub) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (sub.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                contentDescription = if (sub.isArchived) "Unarchive subcategory" else "Archive subcategory",
                                                tint = if (sub.isArchived) TealPrimary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // + Add Subcategory inline button
                    OutlinedButton(
                        onClick = onAddSubcategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .padding(start = 16.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Subcategory", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryInputDialog(
    title: String,
    initialName: String,
    confirmText: String = "Save",
    placeholder: String = "e.g. Entertainment",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        hasError = false
                    },
                    label = { Text("Name") },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    isError = hasError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasError) {
                    Text(
                        text = "Name cannot be empty",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isBlank()) {
                        hasError = true
                    } else {
                        onConfirm(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    contentColor = OnTealPrimary
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
