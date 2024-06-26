import { redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import routes from '$lib/routes';
import loggerFactory from '$lib/logger';
import cookieUtils from '$lib/cookie_utils';

const logger = loggerFactory(import.meta.url);

export const load: PageServerLoad = async () => {
	logger.info('logout load');

	// we only use this endpoint for the api
	// and don't need to see the page
	redirect(302, routes.home);
};

export const actions: Actions = {
	async default({ cookies }) {
		// eat the cookie
		await cookieUtils.clear(cookies);

		// redirect the user
		redirect(302, routes.login);
	}
};
