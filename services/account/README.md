# Cuenta de NÜMA

Servicio de Cloudflare Workers para el acceso con Google, dispositivos y sincronización
de listas, favoritos, artistas, álbumes, carátulas y métricas entre Android y Windows.
Los archivos de audio y las sesiones de proveedores permanecen en cada dispositivo.

## Configuración

1. Ejecutar `npm ci`.
2. Copiar `wrangler.numa.example.toml` y configurar el Worker, la base D1 y los bindings.
3. Crear un cliente OAuth web de Google con la redirección
   `https://<worker>/auth/google/callback` y configurar `GOOGLE_CLIENT_ID`.
4. Cargar `GOOGLE_CLIENT_SECRET` y `SIGNING_KEY` con `wrangler secret put`.
   `npm run generate-key` genera la clave de firma.
5. Aplicar las migraciones y desplegar con la configuración de producción.

La configuración privada está en `wrangler.production.jsonc` y no se versiona.
La instancia de NÜMA usa `https://numa-account.peluchito281.workers.dev`.

## Validación

`npm test` comprueba autenticación, sincronización, permisos y almacenamiento.
El acceso usa PKCE, tokens de corta duración y renovación por dispositivo.
Los secretos se guardan en Cloudflare y nunca se incluyen en las aplicaciones.

Los módulos opcionales de informes, Discord y Connect requieren sus bindings o
credenciales indicados en la configuración de ejemplo. No son necesarios para
sincronizar la biblioteca.
