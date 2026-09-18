import axios from 'axios';

import { ITagSelectable } from 'app/shared/model/tag-selectable.model';

const apiUrl = 'api/tags/selectable';

/** Active, current-owner tags. `includeIds` only displays already-linked inactive historical values. */
export const getSelectableTags = async (includeIds?: number[]): Promise<ITagSelectable[]> => {
  const response = await axios.get<ITagSelectable[]>(apiUrl, {
    // Spring binds a comma-separated request parameter reliably to List<Long>.
    params: includeIds?.length ? { includeId: includeIds.join(',') } : undefined,
  });
  return response.data;
};
