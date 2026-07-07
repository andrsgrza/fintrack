import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';

export interface IUserDashboardPreference {
  id?: number;
  configuration?: string;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  user?: IUser;
}

export const defaultValue: Readonly<IUserDashboardPreference> = {};
