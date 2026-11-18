-- Búsqueda de una emisión por su referencia (HU-33): los últimos 8 caracteres del jti, que van
-- impresos en el comprobante y a la vista en la aplicación. Con este índice la búsqueda no
-- recorre todas las emisiones de la comuna.
CREATE INDEX emision_referencia ON emision (right(id, 8));
