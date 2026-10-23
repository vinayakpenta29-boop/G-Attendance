package com.attendance.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val BrandPrimary = Color(0xFF4F46E5)
private val DangerRed = Color(0xFFEF4444)
private val CardBorder = Color(0xFFE2E8F0)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)

@Composable
fun CalendarPdfDialog(
    initialYear: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var viewYear by remember { mutableStateOf(initialYear) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Dialog state for entering Year before picking file
    var showYearInputDialog by remember { mutableStateOf(false) }
    var targetUploadYear by remember { mutableStateOf(initialYear) }

    // Dialog state for confirming deletion
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // PDF file instance for the currently selected year
    val calendarFile = remember(viewYear, refreshTrigger) {
        File(context.filesDir, "mahalakshmi_calendar_$viewYear.pdf")
    }
    val fileExists = calendarFile.exists()

    // File picker launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val destFile = File(context.filesDir, "mahalakshmi_calendar_$targetUploadYear.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewYear = targetUploadYear
                refreshTrigger++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // PDF Rendering states
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Pinch-to-zoom & pan states
    var scale by remember(currentPageIndex, viewYear, refreshTrigger) { mutableStateOf(1f) }
    var offset by remember(currentPageIndex, viewYear, refreshTrigger) { mutableStateOf(Offset.Zero) }

    // Render page when file or page index changes
    LaunchedEffect(calendarFile.absolutePath, currentPageIndex, refreshTrigger, fileExists) {
        if (!calendarFile.exists()) {
            currentBitmap = null
            pageCount = 0
            return@LaunchedEffect
        }

        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(calendarFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val totalPages = renderer.pageCount
                val safeIndex = currentPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                val page = renderer.openPage(safeIndex)

                // Render at 2.5x density for sharp quality when zooming
                val renderScale = 2.5f
                val width = (page.width * renderScale).toInt()
                val height = (page.height * renderScale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                page.close()
                renderer.close()
                pfd.close()

                withContext(Dispatchers.Main) {
                    pageCount = totalPages
                    currentPageIndex = safeIndex
                    currentBitmap = bitmap
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title & Year Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mahalakshmi Calendar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Year Switcher (‹ Year ›)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "‹",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        viewYear -= 1
                                        currentPageIndex = 0
                                    }
                                    .padding(horizontal = 6.dp)
                            )
                            Text(
                                text = "$viewYear",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "›",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        viewYear += 1
                                        currentPageIndex = 0
                                    }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }

                    // Top Right Action Buttons: Delete (if exists), Plus (+), and Close (✕)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Delete Button (Shown only when a PDF exists for this year)
                        if (fileExists) {
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFEF2F2))
                            ) {
                                Text("🗑️", fontSize = 14.sp)
                            }
                        }

                        // Plus (+) Button to Add PDF for a Year
                        FilledTonalIconButton(
                            onClick = { showYearInputDialog = true },
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = BrandPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        // Close Dialog Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                        ) {
                            Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                    }
                }

                Divider(color = CardBorder, thickness = 1.dp)

                // Body Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A)), // Dark background for crisp contrast
                    contentAlignment = Alignment.Center
                ) {
                    if (fileExists && currentBitmap != null) {
                        // Zoomable & Pannable PDF Viewer Container
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .pointerInput(currentPageIndex, viewYear) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                        val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                        offset = Offset(
                                            x = if (scale > 1f) (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX) else 0f,
                                            y = if (scale > 1f) (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY) else 0f
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = currentBitmap!!.asImageBitmap(),
                                contentDescription = "Mahalakshmi Calendar Page",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )
                        }

                        // Zoom Reset Button (visible when zoomed in)
                        if (scale > 1.05f) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .clickable {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                            ) {
                                Text(
                                    text = "Reset Zoom (%.1fx)".format(scale),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    } else if (isLoading) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        // Empty State when no PDF is found for this year
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("📄", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Calendar PDF Found for $viewYear",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap the '+' button at the top right to upload the Mahalakshmi Calendar PDF for $viewYear.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showYearInputDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                            ) {
                                Text("+ Add $viewYear Calendar PDF")
                            }
                        }
                    }
                }

                // Bottom Page Navigation Bar (when PDF has pages)
                if (fileExists && pageCount > 1) {
                    Divider(color = CardBorder, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (currentPageIndex > 0) {
                                    currentPageIndex -= 1
                                }
                            },
                            enabled = currentPageIndex > 0,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("◀ Prev")
                        }

                        Text(
                            text = "Page ${currentPageIndex + 1} of $pageCount",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        FilledTonalButton(
                            onClick = {
                                if (currentPageIndex < pageCount - 1) {
                                    currentPageIndex += 1
                                }
                            },
                            enabled = currentPageIndex < pageCount - 1,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Next ▶")
                        }
                    }
                }
            }
        }
    }

    // Dialog: Ask for Year before picking PDF
    if (showYearInputDialog) {
        var yearInputText by remember { mutableStateOf(viewYear.toString()) }

        AlertDialog(
            onDismissRequest = { showYearInputDialog = false },
            title = {
                Text("Enter Calendar Year", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column {
                    Text(
                        "Specify which year this calendar PDF belongs to:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = yearInputText,
                        onValueChange = { yearInputText = it },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedYear = yearInputText.trim().toIntOrNull() ?: viewYear
                        targetUploadYear = parsedYear
                        showYearInputDialog = false
                        // Launch system file picker for PDF
                        pdfPickerLauncher.launch("application/pdf")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("OK, Select PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showYearInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Confirm Delete Calendar PDF
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text("Delete Calendar PDF?", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "Are you sure you want to delete the Mahalakshmi Calendar PDF for year $viewYear?",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        if (calendarFile.exists()) {
                            calendarFile.delete()
                        }
                        currentBitmap = null
                        pageCount = 0
                        refreshTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
