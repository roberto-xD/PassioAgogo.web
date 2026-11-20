---
slug: privacidad
titulo: Aviso de privacidad
title_tag: Aviso de privacidad · Passion à gogo
descripcion: Qué datos trata Passion à gogo, cómo se cifra tu dirección de entrega, con quién se comparten y cómo ejercer tus derechos ARCO.
pantalla: PrivacyScreen
actualizado: 2026-11-18
---

Última actualización: 18 de noviembre de 2026.

## 1. Responsable del tratamiento

Roberto A. Gutiérrez C., con domicilio en Velo de Novia 16, Los Ángeles Iztapalapa,
Ciudad de México, es responsable del tratamiento de los datos personales recabados a
través de este sitio. Puedes contactarnos en contacto@passionagogo.com.

## 2. Lo primero: puedes mirar sin dejar rastro

Puedes recorrer el catálogo completo, leer las guías, ver los eventos y escuchar el
podcast **sin crear una cuenta y sin darnos ningún dato personal**. No hay analítica, no
hay píxeles de publicidad y no hay cookies de seguimiento. No sabemos quién nos visita ni
qué mira.

Solo tratamos datos cuando tú decides dar un paso que los requiere: escribirnos, crear
una cuenta, hacer un pedido o girar la ruleta.

## 3. Qué datos tratamos, según lo que hagas

**Si creas una cuenta:**

- Correo electrónico y contraseña. La contraseña no se guarda: se almacena un resumen
  criptográfico del que no puede recuperarse.
- Teléfono, si lo registras.
- Un identificador interno de tu cuenta y una «sal» criptográfica asociada a ella, que no
  es secreta y cuya única función se explica en la sección 4.

**Si haces un pedido:**

- Nombre y teléfono de contacto, para la entrega y para avisarte por WhatsApp.
- Los productos, cantidades e importes del pedido, y las notas que escribas.
- El método de entrega y, si es a domicilio, tu dirección completa, que se cifra como se
  explica en la sección 4.
- El estado del pedido en el tiempo: pago, guía, cancelación, reembolso.

**Si pagas:**

El cobro lo procesa Mercado Pago. Los datos de tu tarjeta se capturan en su plataforma y
**no pasan por este sitio**. De vuelta recibimos y conservamos: el estado del pago, el
identificador de la orden y del pago, el método usado, la marca de la tarjeta y sus
últimos cuatro dígitos, el total y el importe neto que nos liquida Mercado Pago. Nunca el
número completo de la tarjeta ni su código de seguridad.

**Si escribes por el formulario de contacto:**

- El nombre que nos indiques, tu correo y el contenido del mensaje.
- Un valor derivado de tu dirección IP. No almacenamos la IP: guardamos solo un resumen
  criptográfico con sal que no permite reconstruirla, y que usamos únicamente para
  limitar el número de envíos desde un mismo origen.
- La fecha y hora del envío.

**Si giras la ruleta:**

El registro del giro, asociado a tu cuenta, y el código de premio que se haya emitido.
Lo guardamos para que puedas canjearlo y para que la ruleta respete sus propias reglas de
frecuencia.

## 4. Tu dirección de entrega va cifrada

Esta es la parte que más nos importa explicar bien, porque es el dato que más incomoda
dejar en una tienda como esta.

Tu dirección **no se guarda en claro en ningún momento**. Se cifra en tu propio navegador,
antes de salir de tu dispositivo, y lo que llega a nuestra base de datos es un paquete
cifrado. Funciona así:

- Por cada dirección se genera una clave nueva al azar y con ella se cifra el contenido
  (AES-256-GCM).
- Esa clave se guarda envuelta **dos veces**: una con una clave derivada de tu contraseña
  (PBKDF2-SHA256, 210 000 iteraciones, con la sal de tu cuenta) y otra con una clave
  pública de administración.
- La base de datos guarda los tres paquetes y **ninguna clave con la que abrirlos**.

Qué implica en la práctica, dicho sin adornos:

- **Tú** la abres con tu contraseña. La clave derivada vive en la memoria de la pestaña
  mientras la tienes abierta y se borra al cerrarla o al salir de tu cuenta. Tu
  contraseña nunca se guarda, ni en tu dispositivo ni en nuestros servidores.
- **Nosotros** podemos abrirla con la clave privada de administración, y lo hacemos para
  una sola cosa: imprimir la etiqueta de tu paquete. Esa clave privada no está en este
  sitio web, ni en la base de datos, ni en el código: vive fuera, en poder del
  responsable. No te decimos «ni nosotros podemos verla», porque no sería cierto;
  te decimos que nadie más que tú y nosotros puede, y que nosotros solo lo hacemos para
  enviarte el pedido.
- **Un tercero que obtuviera nuestra base de datos** no obtendría direcciones:
  obtendría texto cifrado sin las llaves.
- Si restableces tu contraseña, tu copia de la llave deja de servir y tendrás que volver
  a capturar tu dirección. Es el precio de que no exista una copia de tu llave en ningún
  servidor.

## 5. Sobre el contenido de tu pedido

La ley mexicana considera dato sensible la preferencia sexual, y lo que alguien compra en
una tienda como esta puede dejarla entrever. Lo tratamos en consecuencia:

- El detalle de lo que compraste solo lo ven quienes tienen que prepararlo y enviarlo.
- No se usa para segmentarte, perfilarte ni venderte nada más tarde.
- No se comparte con ninguna red publicitaria, y no hay en el sitio ninguna herramienta
  que pudiera hacerlo sin que lo supiéramos.
- El paquete sale en empaque neutro y el cargo en tu estado de cuenta es neutro, para que
  la compra no se delate sola en tu casa ni en tu banco.
- Ni la paquetería ni Mercado Pago reciben la lista de productos. La paquetería recibe un
  bulto con un peso; Mercado Pago, un importe.

## 6. Para qué usamos tus datos

Finalidades necesarias para darte el servicio:

- Crear y mantener tu cuenta, y permitirte entrar.
- Preparar, cobrar, enviar y dar seguimiento a tu pedido.
- Contactarte por el pedido: confirmación, guía, retrasos, incidencias.
- Atender cancelaciones, reembolsos, devoluciones y garantías.
- Cumplir obligaciones fiscales, contables y legales.
- Prevenir el fraude y el abuso del formulario, de los códigos y de la ruleta.

Finalidades que no son necesarias y a las que puedes oponerte sin perder nada:

- Avisarte de promociones o novedades, si lo autorizas.

No elaboramos perfiles, no tomamos decisiones automatizadas sobre ti, no te enviamos
publicidad no solicitada y **no vendemos ni rentamos tus datos a nadie**.

## 7. Con quién se comparten

No cedemos tus datos a terceros salvo obligación legal. Para operar hace falta apoyarse
en los siguientes proveedores, que actúan como encargados y pueden procesar información
en servidores fuera de México. Cada uno recibe **solo lo que necesita**:

- **Supabase** — base de datos, cuentas y correos de verificación. Recibe tu cuenta y tus
  pedidos; tu dirección, solo cifrada.
- **Hostinger** — alojamiento de los archivos del sitio. Recibe los datos técnicos de la
  conexión.
- **Mercado Pago** — cobro y reembolso. Recibe el importe, y tu nombre y correo para no
  tener que volver a pedírtelos.
- **Envia.com y la paquetería que elijas** — generar la guía y entregar el paquete.
  Reciben el nombre, el teléfono y la dirección de destino, y el peso del bulto.
- **Cloudflare (Turnstile)** — distinguir personas de robots en el formulario. Recibe
  señales técnicas de tu navegador.
- **Google (Firebase)** — avisos internos al equipo cuando entra o cambia un pedido.
  Recibe un aviso con el código del pedido y el nombre.
- **Spotify** — reproductor del podcast. Recibe lo que recoja al cargarse, conforme a su
  propia política.
- **WhatsApp (Meta)** — atención y aviso de la guía. Recibe la conversación que tengas
  con nosotros.

La dirección de entrega es el único punto en que un dato cifrado tiene que descifrarse
para salir: la etiqueta del paquete no puede ir cifrada. Es inevitable, y preferimos
decirlo que insinuar una privacidad que no existe.

## 8. Cookies y almacenamiento local

No usamos cookies propias de publicidad, seguimiento ni analítica. El sitio no tiene
herramientas de estadísticas de navegación.

Para funcionar, la aplicación guarda cosas en el almacenamiento de tu navegador:

- **Tu carrito**, para que no se pierda si cierras la pestaña.
- **Tu sesión**, para que no tengas que volver a entrar en cada visita.
- **La clave derivada de tu contraseña**, en el almacenamiento de sesión, que se borra al
  cerrar la pestaña. Nunca llega al disco ni a nuestros servidores.

Nada de esto sale de tu dispositivo. Puedes borrarlo desde la configuración de tu
navegador, con la salvedad de que perderás el carrito y tendrás que volver a entrar. Los
servicios de terceros de la sección 7 pueden emplear sus propias cookies cuando su
contenido se carga.

## 9. Cuánto tiempo los conservamos

- **Cuenta:** mientras la tengas abierta.
- **Pedidos, pagos y facturación:** el plazo que exija la normativa fiscal y mercantil,
  aunque cierres tu cuenta, porque son operaciones que hay que poder acreditar.
- **Mensajes de contacto:** el tiempo necesario para atenderte y después se eliminan o se
  anonimizan.
- **Giros de la ruleta y códigos:** hasta que caducan, más el periodo necesario para
  resolver cualquier reclamación.

Al cerrar tu cuenta eliminamos lo que no estemos obligados a conservar; el resto se
bloquea y deja de usarse para cualquier finalidad distinta de cumplir esa obligación.

## 10. Tus derechos (ARCO)

Puedes solicitar en cualquier momento:

- **Acceso:** saber qué datos tenemos sobre ti.
- **Rectificación:** corregir los que estén mal.
- **Cancelación:** pedir que se eliminen.
- **Oposición:** pedir que dejemos de usarlos para una finalidad concreta.

También puedes revocar el consentimiento que hubieras dado, y limitar el uso o la
divulgación de tus datos.

Para ejercerlos escríbenos a contacto@passionagogo.com indicando qué solicitas y con qué
cuenta. Podremos pedirte que acredites tu identidad antes de atenderla. Te responderemos
dentro de los plazos que marca la Ley Federal de Protección de Datos Personales en
Posesión de los Particulares.

Si consideras que no atendimos bien tu petición, puedes acudir a la autoridad competente
en materia de protección de datos personales.

## 11. Seguridad

Aplicamos medidas técnicas y organizativas razonables:

- Toda la comunicación con el sitio va por conexión cifrada.
- Tu dirección va cifrada de extremo a extremo, como describe la sección 4.
- El acceso a los pedidos está restringido por cuenta: la base de datos solo te deja leer
  lo tuyo, y solo las columnas que te corresponden. Los importes se calculan en el
  servidor, nunca en tu navegador.
- Las claves de los servicios de cobro y envío viven solo en el servidor; no están en el
  código que descarga tu navegador.
- Las contraseñas no se guardan, y las direcciones IP no se guardan en claro.

Ningún sistema es infalible. Si llegara a producirse una vulneración que afecte de forma
significativa a tus datos, te lo comunicaremos.

## 12. Menores de edad

El sitio está dirigido exclusivamente a personas mayores de 18 años. No recabamos
conscientemente datos de menores. Si detectamos que hemos recibido información de una
persona menor de edad la eliminaremos; si crees que ha ocurrido, escríbenos a
contacto@passionagogo.com.

## 13. Cambios en este aviso

Podemos actualizar este aviso si cambian los servicios del sitio o la normativa
aplicable. La versión vigente será siempre la publicada en esta página, con su fecha de
última actualización. Si el cambio afecta de forma sustancial a cómo tratamos tus datos,
te lo haremos saber por los medios de contacto que tengamos.
