import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { CategoryType } from 'app/shared/model/enumerations/category-type.model';

export interface ICategory {
  id?: number;
  name?: string;
  description?: string | null;
  categoryType?: keyof typeof CategoryType;
  color?: string | null;
  icon?: string | null;
  active?: boolean;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
  parentCategory?: ICategory | null;
}

export const defaultValue: Readonly<ICategory> = {
  active: false,
};
