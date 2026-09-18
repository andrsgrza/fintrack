import axios from 'axios';

import { ICategorySelectable } from 'app/shared/model/category-selectable.model';

const apiUrl = 'api/categories/selectable';

/** Active, current-owner categories. `includeIds` only display existing inactive historical values. */
export const getSelectableCategories = async (includeIds?: number[]): Promise<ICategorySelectable[]> => {
  const response = await axios.get<ICategorySelectable[]>(apiUrl, {
    // Spring binds a comma-separated request parameter reliably to List<Long>.
    params: includeIds?.length ? { includeId: includeIds.join(',') } : undefined,
  });
  return response.data;
};
