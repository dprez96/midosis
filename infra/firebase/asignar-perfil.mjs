// Asigna la comuna y el rol de un usuario de Firebase Authentication.
//
// Van como claims del token: core toma la comuna solo de ahí (HU-28) y el rol decide,
// por ejemplo, quién puede guardar plantillas de posología (HU-03). La consola de Firebase
// no permite editarlos; solo el SDK de administración.
//
// Usa las credenciales de aplicación de quien lo ejecuta, sin archivos de clave:
//   gcloud auth application-default login
//   gcloud auth application-default set-quota-project midosis-desarrollo
//
// Uso:
//   node asignar-perfil.mjs <correo>                   muestra el perfil actual
//   node asignar-perfil.mjs <correo> <comuna> <rol>    lo asigna
//
// Ejemplo:
//   node asignar-perfil.mjs qf@midosis.cl 13123 qf

import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

const PROYECTO = process.env.MIDOSIS_FIREBASE_PROYECTO ?? 'midosis-desarrollo';
const ROLES = {
  auxiliar: 'Auxiliar de farmacia: emite códigos y consulta el catálogo',
  qf: 'Químico farmacéutico: además, administra las plantillas de posología',
  administrador: 'Administrador comunal: exportación e indicadores de su comuna',
};

function salir(mensaje) {
  console.error(mensaje);
  process.exit(1);
}

const [correo, comuna, rol] = process.argv.slice(2);

if (!correo || (comuna && !rol)) {
  salir(
    'Uso:\n' +
      '  node asignar-perfil.mjs <correo>                   muestra el perfil actual\n' +
      '  node asignar-perfil.mjs <correo> <comuna> <rol>    lo asigna\n\n' +
      'Roles: ' + Object.keys(ROLES).join(', '),
  );
}
if (comuna && !/^\d{5}$/.test(comuna)) {
  salir(`La comuna son los cinco dígitos del código del INE; llegó «${comuna}».`);
}
if (rol && !(rol in ROLES)) {
  salir(`Rol desconocido «${rol}». Roles válidos: ${Object.keys(ROLES).join(', ')}.`);
}

initializeApp({ credential: applicationDefault(), projectId: PROYECTO });
const auth = getAuth();

let usuario;
try {
  usuario = await auth.getUserByEmail(correo);
} catch (e) {
  salir(`No existe el usuario ${correo} en el proyecto ${PROYECTO}. Créalo antes en la consola de Firebase.`);
}

if (comuna) {
  // Reemplaza todos los claims del usuario: el perfil es exactamente comuna y rol.
  await auth.setCustomUserClaims(usuario.uid, { comuna, rol });
  usuario = await auth.getUser(usuario.uid);
  console.log(`Perfil asignado en ${PROYECTO}:`);
} else {
  console.log(`Perfil actual en ${PROYECTO}:`);
}

const claims = usuario.customClaims ?? {};
console.log(`  ${usuario.email}`);
console.log(`  comuna: ${claims.comuna ?? '(sin asignar)'}`);
console.log(`  rol:    ${claims.rol ?? '(sin asignar)'}${claims.rol ? `  (${ROLES[claims.rol] ?? 'rol desconocido'})` : ''}`);
if (comuna) {
  console.log('\nEl cambio vale desde el próximo inicio de sesión: los tokens ya emitidos no lo traen.');
}
