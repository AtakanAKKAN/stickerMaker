package com.atakan.stickerlab.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atakan.stickerlab.ui.create.CreateStickerScreen
import com.atakan.stickerlab.ui.packdetail.PackDetailScreen
import com.atakan.stickerlab.ui.packlist.PackListScreen

private const val ROUTE_PACKS = "packs"
private const val ROUTE_PACK_DETAIL = "pack"
private const val ROUTE_CREATE = "create"

@Composable
fun StickerLabNavHost() {
    val navController = rememberNavController()
    val packIdArgument = listOf(navArgument("packId") { type = NavType.LongType })

    NavHost(navController = navController, startDestination = ROUTE_PACKS) {
        composable(ROUTE_PACKS) {
            PackListScreen(
                onOpenPack = { packId -> navController.navigate("$ROUTE_PACK_DETAIL/$packId") },
            )
        }

        composable(route = "$ROUTE_PACK_DETAIL/{packId}", arguments = packIdArgument) {
            PackDetailScreen(
                onBack = { navController.popBackStack() },
                onAddSticker = { packId -> navController.navigate("$ROUTE_CREATE/$packId") },
            )
        }

        composable(route = "$ROUTE_CREATE/{packId}", arguments = packIdArgument) {
            CreateStickerScreen(
                // Photo Picker iptal edildiğinde de buraya düşüyor; her iki
                // durumda da tek yaptığımız geri dönmek.
                onDone = { navController.popBackStack() },
            )
        }
    }
}
