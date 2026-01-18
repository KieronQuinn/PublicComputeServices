package com.kieronquinn.app.pcs.ui.screens.container

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.navigation3.ui.NavDisplay
import com.kieronquinn.app.pcs.R
import com.kieronquinn.app.pcs.repositories.NavigationRepository
import com.kieronquinn.app.pcs.repositories.NavigationRepository.Destination
import com.kieronquinn.app.pcs.repositories.NavigationRepository.MenuItem
import com.kieronquinn.app.pcs.ui.theme.PcsTheme
import com.kieronquinn.app.pcs.utils.extensions.OnResume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.inject
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen() = PcsTheme {
    val viewModel = koinViewModel<ContainerViewModel>()
    val coroutineScope = rememberCoroutineScope()
    val backStack = viewModel.backStack
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val backstackItem = backStack.lastOrNull()
    val showBack = backStack.size > 1
    val hasMenu = backstackItem?.options?.isNotEmpty() ?: false
    val onMenuItemSelected = MutableSharedFlow<MenuItem>()
    val slideInDistance =
        LocalResources.current.getDimensionPixelSize(R.dimen.shared_axis_x_slide_distance)
    var showMenu by remember { mutableStateOf(false) }

    val navigationRepository by inject<NavigationRepository>(
        NavigationRepository::class.java
    )

    val onMenuItemClicked = { item: MenuItem ->
        coroutineScope.launch {
            onMenuItemSelected.emit(item)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Crossfade(backstackItem?.title, label = "title") {
                        it?.let { title ->
                            Text(text = stringResource(title))
                        }
                    }
                },
                navigationIcon = {
                    AnimatedVisibility(
                        visible = showBack,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = {
                                backStack.removeLastOrNull()
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                contentDescription = null
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (hasMenu) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu),
                                contentDescription = stringResource(R.string.menu)
                            )
                        }
                        val items = backstackItem.options
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            items.forEach {
                                DropdownMenuItem(
                                    text = { Text(stringResource(it.text)) },
                                    onClick = {
                                        showMenu = false
                                        onMenuItemClicked(it)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        content = {
            NavDisplay(
                modifier = Modifier.padding(top = it.calculateTopPadding()),
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { destination ->
                    navigationRepository.getNavEntry(destination, onMenuItemSelected)
                },
                transitionSpec = {
                    // If transitioning from loading, don't do the Material slide
                    if(initialState.key.toString() == "Loading") {
                        fadeIn() togetherWith fadeOut()
                    } else {
                        materialSharedAxisXIn(true, slideInDistance) togetherWith
                            materialSharedAxisXOut(true, slideInDistance)
                    }
                },
                popTransitionSpec = {
                    materialSharedAxisXIn(false, slideInDistance) togetherWith
                        materialSharedAxisXOut(false, slideInDistance)
                },
                predictivePopTransitionSpec = {
                    materialSharedAxisXIn(false, slideInDistance) togetherWith
                        materialSharedAxisXOut(false, slideInDistance)
                }
            )
        }
    )

    AnimateAppBarState(backStack, scrollBehavior)

    LaunchedEffect(Unit) {
        navigationRepository.navigationEvent.collect {
            if (it.clear) {
                backStack.clear()
            }
            backStack.add(it.destination)
            showMenu = false
        }
    }

    val darkTheme = isSystemInDarkTheme()
    val window = LocalActivity.current?.window
    LaunchedEffect(darkTheme, window) {
        window?.let {
            WindowCompat.getInsetsController(window, window.decorView).run {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    OnResume {
        viewModel.onResume()
    }
}

/**
 *  Saves the scroll offset for a destination when it's navigated away from and restores it when
 *  it's returned to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimateAppBarState(
    backStack: List<Destination>,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val previousDestination = remember { mutableStateListOf<Destination?>() }
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList() }
            .collect { currentStack ->
                val previous = previousDestination.firstOrNull()
                previous?.let {
                    if (currentStack.contains(it)) {
                        it.appBarOffset = scrollBehavior.state.heightOffset
                    }
                }
                val newDestination = currentStack.lastOrNull()
                val targetOffset = newDestination?.appBarOffset ?: 0f
                val animatable = Animatable(scrollBehavior.state.heightOffset)
                launch {
                    animatable.animateTo(
                        targetValue = targetOffset,
                        animationSpec = tween(durationMillis = 300)
                    ) {
                        scrollBehavior.state.heightOffset = this.value
                    }
                }
                previousDestination.clear()
                previousDestination.add(newDestination)
            }
    }
}
