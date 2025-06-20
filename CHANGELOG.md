---
layout: plain
permalink: /changelog
title: "Changelog"
---

# Changelog for SD Maid 2/SE

{% for release in site.github.releases %}

## {{ release.tag_name }} - {{ release.published_at | date: "%B %d, %Y" }}

{% assign clean_body = release.body | strip %}
{% assign no_comments = clean_body | replace: "<!-- Release notes generated using configuration in .github/release.yml", "" %}
{% assign no_comments = no_comments | split: "-->" %}
{% if no_comments.size > 1 %}
  {% assign clean_content = no_comments[1] | strip %}
{% else %}
  {% assign clean_content = no_comments[0] | strip %}
{% endif %}

{% comment %} Make links clickable {% endcomment %}
{% assign lines = clean_content | split: "
" %}
{% assign processed_lines = "" %}
{% for line in lines %}
  {% if line contains "**Full Changelog**:" %}
    {% comment %} Handle Full Changelog links {% endcomment %}
    {% assign parts = line | split: ": " %}
    {% if parts.size > 1 %}
      {% assign url = parts[1] | strip %}
      {% assign clickable_line = "**[View Changes](" | append: url | append: ")**" %}
      {% assign processed_lines = processed_lines | append: clickable_line | append: "
" %}
    {% else %}
      {% assign processed_lines = processed_lines | append: line | append: "
" %}
    {% endif %}
  {% elsif line contains " in https://github.com/" and line contains "/pull/" %}
    {% comment %} Handle pull request links {% endcomment %}
    {% assign pr_parts = line | split: " in https://github.com/" %}
    {% if pr_parts.size > 1 %}
      {% assign before_url = pr_parts[0] %}
      {% assign after_url = pr_parts[1] %}
      {% assign url = "https://github.com/" | append: after_url %}
      {% assign pr_number = after_url | split: "/pull/" %}
      {% if pr_number.size > 1 %}
        {% assign pr_num = pr_number[1] | split: " " | first %}
        {% assign clickable_line = before_url | append: " in [#" | append: pr_num | append: "](" | append: url | append: ")" %}
        {% assign processed_lines = processed_lines | append: clickable_line | append: "
" %}
      {% else %}
        {% assign processed_lines = processed_lines | append: line | append: "
" %}
      {% endif %}
    {% else %}
      {% assign processed_lines = processed_lines | append: line | append: "
" %}
    {% endif %}
  {% else %}
    {% assign processed_lines = processed_lines | append: line | append: "
" %}
  {% endif %}
{% endfor %}

{% comment %} Add proper spacing between sections and bullet points {% endcomment %}
{% assign final_content = processed_lines | replace: "
### ", "

### " %}
{% assign final_content = final_content | replace: "
## ", "

## " %}
{% assign final_content = final_content | replace: "
- ", "

- " %}

{% comment %} Check if there are any bullet points (actual release notes) {% endcomment %}
{% if final_content contains "## " or final_content contains "- " %}
  {{ final_content | markdownify }}
{% else %}
  *No release notes available.*
  
  {{ final_content | markdownify }}
{% endif %}

---
{% endfor %}
