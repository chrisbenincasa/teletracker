import React from 'react';
import { Link, LinkProps } from 'react-router-dom';

const RouterLink: React.ComponentType<LinkProps> = React.forwardRef(
  (props: LinkProps, ref?: React.Ref<HTMLAnchorElement>) => (
    <Link {...props} innerRef={ref} />
  ),
);

export const StdRouterLink = (
  to: string,
  props: React.HTMLAttributes<HTMLElement>,
) => (
  <RouterLink
    {...props}
    to={to}
    // style={{ textDecoration: 'none' }}
  />
);

export default RouterLink;
