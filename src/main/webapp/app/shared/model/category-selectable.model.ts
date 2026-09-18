import { CategoryType } from 'app/shared/model/enumerations/category-type.model';

/** Product-safe category shape for new assignment controls. */
export interface ICategorySelectable {
  id?: number;
  name?: string;
  categoryType?: keyof typeof CategoryType;
  parentCategoryId?: number | null;
  parentCategoryName?: string | null;
  active?: boolean;
}
