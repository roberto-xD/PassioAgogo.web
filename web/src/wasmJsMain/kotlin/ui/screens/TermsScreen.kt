package ui.screens

import androidx.compose.runtime.Composable
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle

/**
 * Términos y condiciones.
 *
 * Redactado a partir del funcionamiento real del sitio. **Debe revisarlo un profesional
 * legal antes de publicarlo**, y hay que sustituir los marcadores entre corchetes:
 * `[RAZÓN SOCIAL]`, `[DOMICILIO]`, `[CORREO DE CONTACTO]` y `[CIUDAD, ESTADO/PAÍS]`.
 */
@Composable
fun TermsScreen() {
    ContentScreen(title = "Términos y condiciones") {
        Paragraph("Última actualización: 7 de agosto de 2026.")

        SectionTitle("1. Titular del sitio")
        Paragraph(
            "Este sitio web es operado por [RAZÓN SOCIAL], con domicilio en [DOMICILIO] " +
                "(en adelante, \"Passio Agogo\", \"nosotros\"). Para cualquier asunto " +
                "relacionado con estos términos puedes escribirnos desde la sección " +
                "Contacto o a [CORREO DE CONTACTO]."
        )

        SectionTitle("2. Objeto")
        Paragraph(
            "Passio Agogo es un sitio de consulta que reúne y muestra un catálogo de " +
                "productos, junto con contenido informativo y multimedia sobre la marca. " +
                "El sitio tiene una finalidad informativa y de difusión."
        )
        Paragraph(
            "El sitio no procesa compras, pagos ni envíos en línea. La adquisición de " +
                "cualquier producto se realiza por los canales de venta que se indiquen en " +
                "cada caso."
        )

        SectionTitle("3. Aceptación")
        Paragraph(
            "El acceso y uso del sitio implica que aceptas estos términos en su totalidad. " +
                "Si no estás de acuerdo con ellos, te pedimos que no utilices el sitio."
        )

        SectionTitle("4. Uso permitido")
        Paragraph("Al usar el sitio te comprometes a:")
        Paragraph("• Utilizarlo conforme a la ley, a estos términos y a la buena fe.")
        Paragraph(
            "• No interferir con su funcionamiento ni intentar acceder a áreas, sistemas o " +
                "datos que no estén destinados al público."
        )
        Paragraph(
            "• No realizar extracciones masivas o automatizadas de información del catálogo " +
                "sin nuestra autorización previa."
        )
        Paragraph(
            "• No enviar, a través del formulario de contacto, contenido ilícito, " +
                "publicidad no solicitada ni material que vulnere derechos de terceros."
        )

        SectionTitle("5. Catálogo, precios y promociones")
        Paragraph(
            "La información del catálogo —descripciones, imágenes, precios y " +
                "disponibilidad— se ofrece con fines informativos y puede modificarse en " +
                "cualquier momento sin previo aviso."
        )
        Paragraph(
            "Los precios mostrados incluyen, cuando corresponde, las promociones vigentes " +
                "en el momento de la consulta. Las promociones están sujetas a su periodo " +
                "de vigencia y a las condiciones que se indiquen en cada caso."
        )
        Paragraph(
            "Procuramos que la información sea correcta y esté actualizada, pero pueden " +
                "existir errores tipográficos u omisiones. En caso de discrepancia, " +
                "prevalecerá la información confirmada por el canal de venta correspondiente."
        )

        SectionTitle("6. Propiedad intelectual")
        Paragraph(
            "Los contenidos del sitio —incluyendo textos, imágenes, logotipos, material " +
                "audiovisual y su presentación— son titularidad de Passio Agogo o de sus " +
                "respectivos titulares, y están protegidos por la legislación aplicable."
        )
        Paragraph(
            "Se permite la consulta y el uso personal del sitio. Queda prohibida su " +
                "reproducción, distribución o transformación con fines comerciales sin " +
                "autorización previa y por escrito."
        )
        Paragraph(
            "Las marcas y contenidos de terceros que aparezcan en el sitio pertenecen a sus " +
                "respectivos propietarios y se muestran únicamente con fines identificativos."
        )

        SectionTitle("7. Contenido y enlaces de terceros")
        Paragraph(
            "El sitio incorpora contenido servido por terceros, como el reproductor " +
                "incrustado de Spotify, y puede incluir enlaces a sitios externos. No " +
                "controlamos dichos contenidos ni sus condiciones de uso, por lo que no " +
                "asumimos responsabilidad sobre ellos. Al utilizarlos quedas sujeto a los " +
                "términos y políticas de sus respectivos proveedores."
        )

        SectionTitle("8. Disponibilidad del servicio")
        Paragraph(
            "Trabajamos para mantener el sitio accesible, pero no garantizamos su " +
                "disponibilidad ininterrumpida. Podemos suspenderlo temporalmente por " +
                "mantenimiento, actualizaciones o causas ajenas a nosotros."
        )

        SectionTitle("9. Limitación de responsabilidad")
        Paragraph(
            "En la medida permitida por la ley, Passio Agogo no será responsable de los " +
                "daños o perjuicios derivados del uso del sitio, de la imposibilidad de " +
                "usarlo, ni de las decisiones de compra tomadas a partir de la información " +
                "publicada, sin perjuicio de las responsabilidades que la legislación " +
                "aplicable establezca de forma imperativa."
        )

        SectionTitle("10. Protección de datos")
        Paragraph(
            "El tratamiento de los datos personales que nos facilites se rige por nuestra " +
                "Política de privacidad, disponible en este mismo sitio."
        )

        SectionTitle("11. Modificaciones")
        Paragraph(
            "Podemos actualizar estos términos para adaptarlos a cambios en el sitio o en " +
                "la normativa aplicable. La versión vigente será siempre la publicada en " +
                "esta página, con su fecha de última actualización."
        )

        SectionTitle("12. Ley aplicable y jurisdicción")
        Paragraph(
            "Estos términos se rigen por la legislación de [CIUDAD, ESTADO/PAÍS]. Para " +
                "cualquier controversia, las partes se someten a los tribunales competentes " +
                "de dicha jurisdicción, salvo que la normativa de protección al consumidor " +
                "disponga otro fuero."
        )
    }
}
