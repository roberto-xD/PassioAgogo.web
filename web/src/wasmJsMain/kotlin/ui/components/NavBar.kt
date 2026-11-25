package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.navigation.Screen
import ui.theme.PassionTheme
import viewmodel.CatalogSection

/**
 * Por debajo de esto la barra va sin logotipo.
 *
 * El mismo umbral que usa el widget de eventos: una sola idea de «pantalla estrecha»
 * para todo el sitio, en vez de un número distinto en cada componente.
 */
private val COMPACTO_MAX = 640.dp

/** Lado del icono del carrito, dentro de su objetivo táctil. */
private val CART_ICON_SIZE = 22.dp

/** Antes de las secciones del catálogo, que se insertan justo después de Catálogo. */
private val ItemsAntesDeSecciones = listOf(
    Screen.Home,
    Screen.Catalog,
)

/**
 * El resto de la barra.
 *
 * Lo que **no** está aquí y sigue teniendo ruta: Podcast vive ahora al pie de Inicio,
 * la ruleta se abre desde su botón flotante en Inicio, y Contacto y Ayuda se alcanzan
 * desde Nosotros, que los recoge en pestañas y en su botón de escribir. Las rutas
 * siguen existiendo —`#/ayuda`, `#/contacto`, `#/podcast`, `#/ruleta`— porque están
 * enlazadas desde el pie y desde las páginas estáticas de `sitio/`.
 *
 * La barra se desliza en horizontal, pero eso solo se descubre si algo asoma cortado:
 * con diez entradas nadie llegaba al final, y las últimas eran invisibles de hecho.
 */
private val ItemsDespuesDeSecciones = listOf(
    Screen.About,
//    Screen.Video,
    Screen.Events,
    Screen.Care,
)

/**
 * @param sections categorías con pantalla propia (26_category_sections). Vienen de la
 *   base, así que separar un rubro nuevo no toca este archivo.
 * @param currentSectionSlug la sección abierta, para marcarla; `null` fuera de ellas.
 * @param cartCount unidades en el carrito. A cero, la entrada no lleva cuenta.
 */
@Composable
fun NavBar(
    current: Screen,
    isAuthenticated: Boolean,
    onNavigate: (Screen) -> Unit,
    sections: List<CatalogSection> = emptyList(),
    currentSectionSlug: String? = null,
    onNavigateSection: (CatalogSection) -> Unit = {},
    cartCount: Int = 0,
) {
    // El ancho se mide por fuera del `Row`: dentro de uno que se desplaza en
    // horizontal las restricciones son infinitas y `maxWidth` no diría nada.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compacto = maxWidth < COMPACTO_MAX

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PassionTheme.semantics.barSurface)
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = PassionTheme.spacing.s5,
                    vertical = PassionTheme.spacing.s3,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s6),
        ) {
            // En pantalla estrecha el logotipo no va.
            //
            // Ocupaba media barra y empujaba los enlaces fuera de la vista, así que la
            // barra parecía terminar donde terminaba la pantalla y nadie adivinaba que se
            // desliza. Sin él caben cuatro entradas y la quinta asoma cortada por la
            // derecha, que es lo que de verdad dice «aquí hay más».
            //
            // No se pierde el camino a casa: «Inicio» es la primera entrada, y el logotipo
            // sigue en el pie.
            if (!compacto) {
                Text(
                    text = "Passion à gogo",
                    color = MaterialTheme.colorScheme.primary,
                    style = PassionTheme.type.scriptAccent,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable { onNavigate(Screen.Home) },
                )
                Spacer(Modifier.width(PassionTheme.spacing.s2))
            }
            ItemsAntesDeSecciones.forEach { screen ->
                NavItem(
                    label = screen.title,
                    selected = screen == current,
                    onClick = { onNavigate(screen) },
                )
            }
            // Las secciones van pegadas a Catálogo: son catálogo, solo que aparte.
            sections.forEach { section ->
                NavItem(
                    label = section.nombre,
                    selected = current == Screen.Section && section.slug == currentSectionSlug,
                    onClick = { onNavigateSection(section) },
                )
            }
            ItemsDespuesDeSecciones.forEach { screen ->
                NavItem(
                    label = screen.title,
                    selected = screen == current,
                    onClick = { onNavigate(screen) },
                )
            }

            // Último elemento: acceso o cuenta, según haya sesión iniciada.
            val accountScreen = if (isAuthenticated) Screen.Account else Screen.Login
            NavItem(
                label = accountScreen.title,
                selected = current == Screen.Login || current == Screen.Account,
                onClick = { onNavigate(accountScreen) },
            )

            // El carrito va al final, como icono: dice lo mismo que «Mi solicitud (3)»
            // en una cuarta parte del ancho, y ese ancho es justo lo que le faltaba a la
            // barra. La cuenta sigue a la vista en el globo.
            CartItem(
                count = cartCount,
                selected = current == Screen.Cart,
                onClick = { onNavigate(Screen.Cart) },
            )
        }
    }
}

/**
 * El carrito: una bolsa dibujada, con la cuenta en un globo.
 *
 * El icono se dibuja a mano en lugar de traer la biblioteca de iconos de Material, que
 * por un solo glifo añadiría varios megabytes al `.wasm`. Son cuatro trazos.
 */
@Composable
private fun CartItem(count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) PassionTheme.semantics.onBackgroundStrong
    else PassionTheme.semantics.onBackgroundMuted

    Box(
        modifier = Modifier
            // Objetivo táctil holgado alrededor de un icono pequeño.
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(CART_ICON_SIZE)) {
            val grosor = size.width * 0.09f
            // El cuerpo de la bolsa.
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.12f, size.height * 0.34f),
                size = Size(size.width * 0.76f, size.height * 0.56f),
                cornerRadius = CornerRadius(size.width * 0.13f),
                style = Stroke(width = grosor),
            )
            // El asa: media circunferencia apoyada en la boca de la bolsa.
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.30f, size.height * 0.14f),
                size = Size(size.width * 0.40f, size.height * 0.40f),
                style = Stroke(width = grosor, cap = StrokeCap.Round),
            )
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Por encima de 9 el globo crecería hasta tapar la bolsa, y el
                    // número exacto ya se ve al entrar.
                    text = if (count > 9) "9+" else count.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) PassionTheme.semantics.onBackgroundStrong
        else PassionTheme.semantics.onBackgroundMuted,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = PassionTheme.spacing.s1),
    )
}
