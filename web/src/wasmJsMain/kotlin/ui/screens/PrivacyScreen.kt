package ui.screens

import androidx.compose.runtime.Composable
import ui.components.ContentScreen
import ui.components.Paragraph
import ui.components.SectionTitle

/**
 * Política de privacidad.
 *
 * Describe el tratamiento de datos que hace realmente esta aplicación: el formulario de
 * contacto (nombre, correo y mensaje), el hash del IP usado para limitar envíos, y los
 * terceros implicados (Supabase, Cloudflare Turnstile y el contenido incrustado de
 * Spotify). Completado con los datos reales del titular. **Debe revisarlo un profesional
 * legal antes de publicarlo.**
 *
 * Importante: si se añaden analítica, cuentas de usuario o nuevos servicios de terceros,
 * este texto debe actualizarse.
 */
@Composable
fun PrivacyScreen() {
    ContentScreen(title = "Política de privacidad") {
        Paragraph("Última actualización: 7 de agosto de 2026.")

        SectionTitle("1. Responsable del tratamiento")
        Paragraph(
            "Roberto Gutiérrez, con domicilio en Velo de novia 16, Los Ángeles Iztapalapa CDMX, es responsable del tratamiento " +
                "de los datos personales recabados a través de este sitio. Puedes " +
                "contactarnos en contacto@passionagogo.com."
        )

        SectionTitle("2. Qué datos recopilamos")
        Paragraph(
            "Puedes navegar por el catálogo y el resto del sitio sin proporcionar ningún " +
                "dato personal ni crear una cuenta."
        )
        Paragraph("Únicamente tratamos datos cuando utilizas el formulario de contacto:")
        Paragraph("• Nombre que nos indiques.")
        Paragraph("• Correo electrónico.")
        Paragraph("• Contenido del mensaje.")
        Paragraph(
            "• Un valor derivado de tu dirección IP. No almacenamos la dirección IP: " +
                "guardamos únicamente un resumen criptográfico (hash con sal) que no " +
                "permite reconstruirla, y que usamos solo para limitar el número de envíos " +
                "desde un mismo origen y evitar el uso abusivo del formulario."
        )
        Paragraph("• Fecha y hora del envío.")

        SectionTitle("3. Para qué usamos tus datos")
        Paragraph("• Atender y responder tu solicitud o consulta.")
        Paragraph("• Mantener un registro de las comunicaciones recibidas.")
        Paragraph("• Proteger el formulario frente a envíos automatizados y abuso.")
        Paragraph(
            "No utilizamos tus datos para elaborar perfiles ni para enviarte comunicaciones " +
                "comerciales no solicitadas, y no los vendemos a terceros."
        )

        SectionTitle("4. Legitimación")
        Paragraph(
            "El tratamiento se basa en tu consentimiento, que otorgas al enviar el " +
                "formulario, y en nuestro interés legítimo en mantener la seguridad del " +
                "sitio y responder a las solicitudes que recibimos."
        )

        SectionTitle("5. Conservación")
        Paragraph(
            "Conservamos los mensajes durante el tiempo necesario para atender tu solicitud " +
                "y, posteriormente, durante el plazo que exija la normativa aplicable. " +
                "Después se eliminan o se anonimizan."
        )

        SectionTitle("6. Proveedores y destinatarios")
        Paragraph(
            "No cedemos tus datos a terceros, salvo obligación legal. Para operar el sitio " +
                "nos apoyamos en los siguientes proveedores, que actúan como encargados del " +
                "tratamiento y pueden procesar información en servidores ubicados fuera de " +
                "tu país:"
        )
        Paragraph(
            "• Supabase — alojamiento de la base de datos y de los archivos del sitio, " +
                "donde se almacenan los mensajes del formulario."
        )
        Paragraph(
            "• Cloudflare — servicio Turnstile de verificación anti-robots, que analiza " +
                "señales técnicas de tu navegador para distinguir personas de programas " +
                "automatizados."
        )
        Paragraph(
            "• Spotify — el reproductor incrustado en la sección Podcast es contenido " +
                "servido por Spotify. Al cargarse, Spotify puede recibir datos de tu " +
                "navegación y utilizar sus propias cookies conforme a su política de " +
                "privacidad, sobre la que no tenemos control."
        )

        SectionTitle("7. Cookies y almacenamiento local")
        Paragraph(
            "No utilizamos cookies propias con fines publicitarios, de seguimiento ni de " +
                "analítica: actualmente el sitio no incorpora herramientas de estadísticas " +
                "de navegación."
        )
        Paragraph(
            "La aplicación puede guardar información técnica en el almacenamiento local de " +
                "tu navegador para su funcionamiento. Los servicios de terceros mencionados " +
                "en el apartado anterior pueden emplear sus propias cookies o tecnologías " +
                "equivalentes cuando su contenido se carga en el sitio."
        )
        Paragraph(
            "Puedes borrar o bloquear estos datos desde la configuración de tu navegador, " +
                "teniendo en cuenta que algunas funciones podrían dejar de estar disponibles."
        )

        SectionTitle("8. Tus derechos")
        Paragraph(
            "Puedes solicitar en cualquier momento el acceso a tus datos personales, así " +
                "como su rectificación, cancelación o eliminación, oponerte a su tratamiento " +
                "o retirar el consentimiento que hubieras otorgado."
        )
        Paragraph(
            "Para ejercerlos, escríbenos a contacto@passionagogo.com indicando la solicitud " +
                "concreta. Podremos pedirte que acredites tu identidad antes de atenderla. " +
                "Si consideras que no hemos atendido correctamente tu petición, puedes " +
                "acudir a la autoridad de protección de datos que corresponda."
        )

        SectionTitle("9. Seguridad")
        Paragraph(
            "Aplicamos medidas técnicas y organizativas razonables para proteger la " +
                "información: la comunicación con el sitio se realiza mediante conexión " +
                "cifrada, el acceso a los mensajes recibidos está restringido al personal " +
                "autorizado y, como se indica arriba, no almacenamos direcciones IP en claro."
        )

        SectionTitle("10. Menores de edad")
        Paragraph(
            "El sitio está dirigido exclusivamente a personas mayores de 18 años, según se " +
                "detalla en los Términos y condiciones. No recabamos conscientemente datos " +
                "de personas menores de edad."
        )
        Paragraph(
            "Si detectamos que hemos recibido información de una persona menor de edad, " +
                "procederemos a eliminarla. Si crees que esto ha ocurrido, escríbenos a " +
                "contacto@passionagogo.com para que la suprimamos."
        )

        SectionTitle("11. Cambios en esta política")
        Paragraph(
            "Podemos actualizar esta política si cambian los servicios del sitio o la " +
                "normativa aplicable. La versión vigente será siempre la publicada en esta " +
                "página, con su fecha de última actualización."
        )
    }
}
